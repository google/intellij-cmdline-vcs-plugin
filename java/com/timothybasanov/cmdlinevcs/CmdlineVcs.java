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

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Throwable2Computable;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManagerGate;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.ChangelistBuilder;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.TextRevisionNumber;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import com.intellij.openapi.vcs.impl.ContentRevisionCache.UniqueType;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;

import net.sf.cglib.proxy.Proxy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * IntelliJ-specific boilerplate to call into Cmdline-specific code in {@link CmdlineUtils}.
 */
public class CmdlineVcs extends AbstractVcs<CommittedChangeList> {

  static final VcsKey vcsKey = createKey("Cmdline");

  public CmdlineVcs(@NotNull Project project) {
    super(project, "Cmdline");
  }

  @Override
  public String getDisplayName() {
    return "Command-line VCS";
  }

  @Override
  public Configurable getConfigurable() {
    return (Configurable)
        Proxy.newProxyInstance(
            this.getClass().getClassLoader(), new Class[] {Configurable.class}, null);
  }

  /**
   * Checks with VCS on any suspicion that file may be under VCS.
   */
  @Nullable
  @Override
  public ChangeProvider getChangeProvider() {
    return new ChangeProvider() {
      @Override
      public void getChanges(
          VcsDirtyScope dirtyScope,
          ChangelistBuilder builder,
          ProgressIndicator progress,
          ChangeListManagerGate addGate)
          throws VcsException {
        for (VirtualFile contentRoot : dirtyScope.getAffectedContentRoots()) {
          Map<FilePath, String> baseFileRevisions = CmdlineUtils.getBaseFileRevisions(contentRoot);
          for (final FilePath path : baseFileRevisions.keySet()) {
            String revisionString = baseFileRevisions.get(path);
            final VcsRevisionNumber revision =
                "NULL".equals(revisionString)
                    ? VcsRevisionNumber.NULL
                    : new TextRevisionNumber(revisionString) {
                      @Override
                      public int hashCode() {
                        return asString().hashCode();
                      }

                      @Override
                      public boolean equals(Object obj) {
                        return obj instanceof TextRevisionNumber
                            && asString().equals(((TextRevisionNumber) obj).asString());
                      }
                    };
            builder.processChange(
                new Change(
                    revision == VcsRevisionNumber.NULL
                        ? null
                        : new ContentRevision() {
                          @Nullable
                          @Override
                          public String getContent() throws VcsException {
                            try {
                              return ContentRevisionCache.getOrLoadAsString(
                                  getProject(),
                                  path,
                                  revision,
                                  getKeyInstanceMethod(),
                                  UniqueType.REPOSITORY_CONTENT,
                                  new Throwable2Computable<byte[], VcsException, IOException>() {
                                    @Override
                                    public byte[] compute() throws VcsException, IOException {
                                      return CmdlineUtils.getBaseFileContent(path, revision);
                                    }
                                  });
                            } catch (IOException e) {
                              throw new VcsException(e);
                            }
                          }

                          @NotNull
                          @Override
                          public FilePath getFile() {
                            return path;
                          }

                          @NotNull
                          @Override
                          public VcsRevisionNumber getRevisionNumber() {
                            return revision;
                          }
                        },
                    path.getVirtualFile() == null ? null : CurrentContentRevision.create(path)),
                getKeyInstanceMethod());

            progress.checkCanceled();
          }
        }
      }

      @Override
      public boolean isModifiedDocumentTrackingRequired() {
        return false;
      }

      @Override
      public void doCleanup(List<VirtualFile> files) {}
    };
  }
}
