// Copyright 2015 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.timothybasanov.cmdlinevcs;

import com.google.common.base.Preconditions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class executing stuff on the command-line via helper utilities.
 */
public class CmdlineUtils {

  /**
   * Return all modified, deleted and added files from the point of view of Vcs.
   *
   * <p>Calls into {@code cmdline-list-changed-files} and expects to see a list of {@code
   * filePath;revision} with an absolute path and a revision as a version of a file in the repo.
   * {@code NULL} is a special revision to signal that this file was just added.</p>
   *
   * @param directory passed as a current directory to the command-line tool
   */
  public static Map<FilePath, String> getBaseFileRevisions(VirtualFile directory)
      throws VcsException {
    GeneralCommandLine commandLine =
        new GeneralCommandLine("/usr/local/bin/cmdline-vcs", "list-changed-files", directory
            .getPath())
            .withWorkDirectory(directory.getPath());
    ProcessOutput processOutput;
    try {
      processOutput = ExecUtil.execAndGetOutput(commandLine);
    } catch (ExecutionException e) {
      throw new VcsException(e);
    }
    if (processOutput.getExitCode() != 0) {
      throw new VcsException("Could not list edited files in " + directory);
    }

    Pattern linePattern = Pattern.compile(
        "([^;]+);([^;]+)");

    Map<FilePath, String> result = new HashMap<>();
    for (String line : processOutput.getStdoutLines(true)) {
      Matcher matcher = linePattern.matcher(line);
      if (!matcher.matches()) {
        throw new VcsException("Fail to parse script output");
      }
      String path = matcher.group(1);
      Preconditions.checkNotNull(path, "path");
      String revision = matcher.group(2);
      Preconditions.checkNotNull(revision, "revision");

      result.put(VcsUtil.getFilePath(path), revision);
    }
    return result;
  }

  /**
   * Get the base content for a file to diff against in the editor.
   *
   * <p>Calls into {@code cmdline-base-file-contents} with a working directory of a file. Two
   * parameters are passed: absolute filename and revision number from previous call.</p>
   *
   * @param file to a file which base revision should be loaded
   * @param revision revision returned by {@link #getBaseFileRevisions} for this file
   */
  public static byte[] getBaseFileContent(FilePath file, VcsRevisionNumber revision)
      throws VcsException {
    @Nullable
    String workingDirectory =
        file.getParentPath() != null ? file.getParentPath().getPath() : null;
    GeneralCommandLine commandLine = new GeneralCommandLine("/usr/local/bin/cmdline-vcs",
        "get-base-file-contents",
        file.getPath(),
        revision.asString()).withWorkDirectory(workingDirectory);
    ProcessOutput processOutput;
    try {
      processOutput = ExecUtil.execAndGetOutput(commandLine);
    } catch (ExecutionException e) {
      throw new VcsException(e);
    }
    if (processOutput.getExitCode() != 0) {
      throw new VcsException("Could not get file contents " + file);
    }

    return processOutput.getStdout().getBytes(CharsetToolkit.getDefaultSystemCharset());
  }

  /**
   * Tries to find a VCS root from the current directory.
   *
   * <p>Calls into {@code cmdline-get-vcs-root} with a working directory of a file.</p>
   *
   * @param directory to a file which base revision should be loaded
   */
  @Nullable
  public static VirtualFile getVcsRoot(VirtualFile directory) {
    String workingDirectory = directory.getPath();
    GeneralCommandLine commandLine =
        new GeneralCommandLine("/usr/local/bin/cmdline-vcs", "get-vcs-root", directory.getPath())
            .withWorkDirectory(workingDirectory);
    ProcessOutput processOutput;
    try {
      processOutput = ExecUtil.execAndGetOutput(commandLine);
    } catch (ExecutionException e) {
      return null;
    }
    if (processOutput.getExitCode() != 0) {
      return null;
    }

    List<String> lines = processOutput.getStdoutLines(true);
    for (String line : lines) {
      return VcsUtil.getVirtualFile(line);
    }
    return null;
  }
}
