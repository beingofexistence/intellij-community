// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.impl.VfsData
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry
import com.intellij.util.application
import com.intellij.util.asSafely
import com.intellij.util.indexing.dependencies.AppIndexingDependenciesService
import com.intellij.util.indexing.dependencies.FileIndexingStamp
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService
import com.intellij.util.indexing.impl.perFileVersion.IntFileAttribute
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * An object dedicated to manage persistent `isIndexed` file flag.
 */
@ApiStatus.Internal
object IndexingFlag {
  private val persistence = IntFileAttribute.create("indexing.flag", 0, true)
  private val hashes = StripedIndexingStampLock()

  @JvmStatic
  val nonExistentHash: Long = StripedIndexingStampLock.NON_EXISTENT_HASH

  @JvmStatic
  fun cleanupProcessedFlag() {
    application.service<AppIndexingDependenciesService>().invalidateAllStamps()
  }

  private fun VirtualFile.asApplicable(): VirtualFileWithId? {
    return asSafely<VirtualFileWithId>()?.let { if (VfsData.isIsIndexedFlagDisabled()) null else it }
  }

  @JvmStatic
  fun cleanProcessedFlagRecursively(file: VirtualFile) {
    file.asApplicable()?.also { fileWithId ->
      cleanProcessingFlag(file)
      if (fileWithId is VirtualFileSystemEntry) {
        if (fileWithId.isDirectory()) {
          for (child in fileWithId.cachedChildren) {
            cleanProcessedFlagRecursively(child)
          }
        }
      }
    }
  }

  @JvmStatic
  fun cleanProcessingFlag(file: VirtualFile) {
    setFileIndexed(file, ProjectIndexingDependenciesService.NULL_STAMP)
  }

  @JvmStatic
  fun setFileIndexed(file: VirtualFile, stamp: FileIndexingStamp) {
    file.asApplicable()?.also { fileWithId ->
      stamp.store { s ->
        persistence.writeInt(fileWithId.id, s)
      }
    }
  }

  @JvmStatic
  fun isFileIndexed(file: VirtualFile, stamp: FileIndexingStamp): Boolean {
    return file.asApplicable()?.let { fileWithId ->
      stamp.isSame(persistence.readInt(fileWithId.id))
    } ?: false
  }

  @JvmStatic
  fun getOrCreateHash(file: VirtualFile): Long {
    return file.asApplicable()?.let { fileWithId -> hashes.getHash(fileWithId.id) } ?: nonExistentHash
  }

  @JvmStatic
  fun unlockFile(file: VirtualFile) {
    file.asApplicable()?.also { fileWithId -> hashes.releaseHash(fileWithId.id) }
  }

  @JvmStatic
  fun setIndexedIfFileWithSameLock(file: VirtualFile, lockObject: Long, stamp: FileIndexingStamp) {
    file.asApplicable()?.also { fileWithId ->
      val hash = hashes.releaseHash(fileWithId.id)
      if (!isFileIndexed(file, stamp)) {
        if (hash == lockObject) {
          setFileIndexed(file, stamp)
        }
        else {
          cleanProcessingFlag(file)
        }
      }
    }
  }

  @JvmStatic
  fun unlockAllFiles() {
    hashes.clear()
  }

  @JvmStatic
  @TestOnly
  fun dumpLockedFiles(): IntArray = hashes.dumpIds()
}