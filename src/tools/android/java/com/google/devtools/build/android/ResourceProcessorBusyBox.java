// Copyright 2017 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.android;

import com.google.devtools.build.android.AndroidResourceMerger.MergingException;
import com.google.devtools.build.android.aapt2.Aapt2Exception;
import com.google.devtools.build.android.resources.JavaIdentifierValidator.InvalidJavaIdentifier;
import com.google.devtools.build.lib.worker.WorkerProtocol.WorkRequest;
import com.google.devtools.build.lib.worker.WorkerProtocol.WorkResponse;
import com.google.devtools.common.options.EnumConverter;
import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionDocumentationCategory;
import com.google.devtools.common.options.OptionEffectTag;
import com.google.devtools.common.options.OptionsBase;
import com.google.devtools.common.options.OptionsParser;
import com.google.devtools.common.options.OptionsParsingException;
import com.google.devtools.common.options.ShellQuotedParamsFilePreProcessor;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides an entry point for the resource processing stages.
 *
 * <p>A single entry point simplifies the build tool binary configuration and keeps the size of tool
 * jar small, as opposed to multiple tools for each process step. It also makes it easy to prototype
 * changes in the resource processing system.
 *
 * <pre>
 * Example Usage:
 *   java/com/google/devtools/build/android/ResourceProcessorBusyBox\
 *      --tool PACKAGE\
 *      --sdkRoot path/to/sdk\
 *      --aapt path/to/sdk/aapt\
 *      --adb path/to/sdk/adb\
 *      --zipAlign path/to/sdk/zipAlign\
 *      --androidJar path/to/sdk/androidJar\
 *      --manifestOutput path/to/manifest\
 *      --primaryData path/to/resources:path/to/assets:path/to/manifest\
 *      --data p/t/res1:p/t/assets1:p/t/1/AndroidManifest.xml:p/t/1/R.txt:symbols,\
 *             p/t/res2:p/t/assets2:p/t/2/AndroidManifest.xml:p/t/2/R.txt:symbols\
 *      --packagePath path/to/write/archive.ap_\
 *      --srcJarOutput path/to/write/archive.srcjar
 * </pre>
 */
public class ResourceProcessorBusyBox {
  static enum Tool {
    PACKAGE() {
      @Override
      void call(String[] args) throws Exception {
        AndroidResourceProcessingAction.main(args);
      }
    },
    VALIDATE() {
      @Override
      void call(String[] args) throws Exception {
        AndroidResourceValidatorAction.main(args);
      }
    },
    GENERATE_BINARY_R() {
      @Override
      void call(String[] args) throws Exception {
        RClassGeneratorAction.main(args);
      }
    },
    GENERATE_LIBRARY_R() {
      @Override
      void call(String[] args) throws Exception {
        LibraryRClassGeneratorAction.main(args);
      }
    },
    GENERATE_ROBOLECTRIC_R() {
      @Override
      void call(String[] args) throws Exception {
        GenerateRobolectricResourceSymbolsAction.main(args);
      }
    },
    PARSE() {
      @Override
      void call(String[] args) throws Exception {
        AndroidResourceParsingAction.main(args);
      }
    },
    MERGE() {
      @Override
      void call(String[] args) throws Exception {
        AndroidResourceMergingAction.main(args);
      }
    },
    MERGE_COMPILED() {
      @Override
      void call(String[] args) throws Exception {
        AndroidCompiledResourceMergingAction.main(args);
      }
    },
    GENERATE_AAR() {
      @Override
      void call(String[] args) throws Exception {
        AarGeneratorAction.main(args);
      }
    },
    SHRINK() {
      @Override
      void call(String[] args) throws Exception {
        ResourceShrinkerAction.main(args);
      }
    },
    MERGE_MANIFEST() {
      @Override
      void call(String[] args) throws Exception {
        ManifestMergerAction.main(args);
      }
    },
    COMPILE_LIBRARY_RESOURCES() {
      @Override
      void call(String[] args) throws Exception {
        CompileLibraryResourcesAction.main(args);
      }
    },
    LINK_STATIC_LIBRARY() {
      @Override
      void call(String[] args) throws Exception {
        ValidateAndLinkResourcesAction.main(args);
      }
    },
    AAPT2_PACKAGE() {
      @Override
      void call(String[] args) throws Exception {
        Aapt2ResourcePackagingAction.main(args);
      }
    },
    SHRINK_AAPT2() {
      @Override
      void call(String[] args) throws Exception {
        Aapt2ResourceShrinkingAction.main(args);
      }
    },
    MERGE_ASSETS() {
      @Override
      void call(String[] args) throws Exception {
        AndroidAssetMergingAction.main(args);
      }
    },
    PROCESS_DATABINDING {
      @Override
      void call(String[] args) throws Exception {
        AndroidDataBindingProcessingAction.main(args);
      }
    };

    abstract void call(String[] args) throws Exception;
  }

  private static final Logger logger = Logger.getLogger(ResourceProcessorBusyBox.class.getName());

  /** Converter for the Tool enum. */
  public static final class ToolConverter extends EnumConverter<Tool> {

    public ToolConverter() {
      super(Tool.class, "resource tool");
    }
  }

  /** Flag specifications for this action. */
  public static final class Options extends OptionsBase {
    @Option(
        name = "tool",
        defaultValue = "null",
        converter = ToolConverter.class,
        category = "input",
        documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
        effectTags = {OptionEffectTag.UNKNOWN},
        help =
            "The processing tool to execute. "
                + "Valid tools: PACKAGE, VALIDATE, GENERATE_BINARY_R, GENERATE_LIBRARY_R, PARSE, "
                + "MERGE, GENERATE_AAR, SHRINK, MERGE_MANIFEST, COMPILE_LIBRARY_RESOURCES, "
                + "LINK_STATIC_LIBRARY, AAPT2_PACKAGE, SHRINK_AAPT2, MERGE_COMPILED.")
    public Tool tool;
  }

  public static void main(String[] args) throws Exception {
    // It's cheaper and cleaner to detect for a single flag to start worker mode without having to
    // initialize Options/OptionsParser here. This keeps the processRequest interface minimal and
    // minimizes moving option state between these methods.
    if (args.length == 1 && args[0].equals("--persistent_worker")) {
      System.exit(runPersistentWorker());
    } else {
      System.exit(processRequest(Arrays.asList(args)));
    }
  }

  private static int runPersistentWorker() throws Exception {
    while (true) {
      try {
        WorkRequest request = WorkRequest.parseDelimitedFrom(System.in);

        if (request == null) {
          break;
        }

        int exitCode = processRequest(request.getArgumentsList());
        WorkResponse.newBuilder()
            .setExitCode(exitCode)
            .build()
            .writeDelimitedTo(System.out);
        System.out.flush();
      } catch (IOException e) {
        logger.severe(e.getMessage());
        e.printStackTrace();
        return 1;
      }
    }
    return 0;
  }

  private static int processRequest(List<String> args) throws Exception {
    OptionsParser optionsParser =
        OptionsParser.newOptionsParser(
            true, new ShellQuotedParamsFilePreProcessor(FileSystems.getDefault()), Options.class);
    Options options;
    try {
      optionsParser.parse(args);
      options = optionsParser.getOptions(Options.class);
      options.tool.call(optionsParser.getResidue().toArray(new String[0]));
    } catch (
        OptionsParsingException
        | MergingException
        | IOException
        | Aapt2Exception
        | InvalidJavaIdentifier e) {
      logSuppressed(e);
      throw e;
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error during processing", e);
      throw e;
    }
    return 0;
  }

  private static void logSuppressed(Throwable e) {
    Arrays.stream(e.getSuppressed()).map(Throwable::getMessage).forEach(logger::severe);
  }
}
