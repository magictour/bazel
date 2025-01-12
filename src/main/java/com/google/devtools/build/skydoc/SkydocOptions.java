// Copyright 2018 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.skydoc;

import com.google.devtools.common.options.EnumConverter;
import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionDocumentationCategory;
import com.google.devtools.common.options.OptionEffectTag;
import com.google.devtools.common.options.OptionsBase;
import java.util.List;

/** Contains options for running {@link SkydocMain}. */
public class SkydocOptions extends OptionsBase {

  @Option(
      name = "input",
      defaultValue = "",
      documentationCategory = OptionDocumentationCategory.UNDOCUMENTED,
      effectTags = OptionEffectTag.UNKNOWN,
      help = "The label of the target file for which to generate documentation")
  public String targetFileLabel;

  @Option(
      name = "workspace_name",
      defaultValue = "",
      documentationCategory = OptionDocumentationCategory.UNDOCUMENTED,
      effectTags = OptionEffectTag.UNKNOWN,
      help = "The name of the workspace in which the input file resides")
  public String workspaceName;

  @Option(
      name = "output",
      defaultValue = "",
      documentationCategory = OptionDocumentationCategory.UNDOCUMENTED,
      effectTags = OptionEffectTag.UNKNOWN,
      help = "The path of the file to output documentation into")
  public String outputFilePath;

  @Option(
      name = "output_format",
      defaultValue = "markdown",
      converter = OutputFormatConverter.class,
      documentationCategory = OptionDocumentationCategory.UNDOCUMENTED,
      effectTags = OptionEffectTag.UNKNOWN,
      help = "The format choice for the output file (\"markdown\" or \"proto\").")
  public OutputFormat outputFormat;

  @Option(
      name = "symbols",
      allowMultiple = true,
      defaultValue = "",
      documentationCategory = OptionDocumentationCategory.UNDOCUMENTED,
      effectTags = OptionEffectTag.UNKNOWN,
      help = "The path of the file to output documentation into")
  public List<String> symbolNames;

  @Option(
      name = "dep_roots",
      allowMultiple = true,
      defaultValue = "",
      documentationCategory = OptionDocumentationCategory.UNDOCUMENTED,
      effectTags = OptionEffectTag.UNKNOWN,
      help = "File path roots to search when resolving transitive bzl dependencies")
  public List<String> depRoots;

  /** Converter for {@link OutputFormat} */
  public static class OutputFormatConverter extends EnumConverter<OutputFormat> {

    public OutputFormatConverter() {
      super(OutputFormat.class, "output format");
    }
  }

  /**
   * The possible values for the --output_format flag, which controls the format of Stardoc's output
   * file.
   */
  public enum OutputFormat {
    MARKDOWN,
    PROTO
  }
}
