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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * IntelliJ-specific magic bits to autodetect VCS roots.
 */
public class CmdlineVcsRootChecker extends VcsRootChecker {

  /**
   * Command-line utilities are not fast, caching is a must.
   */
  private static Cache<String, Boolean> isRootCache =
      CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build();

  @Override
  public VcsKey getSupportedVcs() {
    return CmdlineVcs.vcsKey;
  }

  @Override
  public boolean isRoot(@NotNull String pathString) {
    Boolean cachedIsRoot = isRootCache.getIfPresent(pathString);
    if (cachedIsRoot != null) {
      return cachedIsRoot;
    }
    VirtualFile path = VcsUtil.getVirtualFile(pathString);
    VirtualFile vcsRoot = CmdlineUtils.getVcsRoot(path);
    if (vcsRoot == null) {
      while (path != null) {
        isRootCache.put(path.getPath(), false);
        path = path.getParent();
      }
      return false;
    }

    while (path != null) {
      boolean root = path.getPath().equals(vcsRoot.getPath());
      isRootCache.put(path.getPath(), root);
      if (root) {
        return true;
      }
      path = path.getParent();
    }
    return false;
  }
}
