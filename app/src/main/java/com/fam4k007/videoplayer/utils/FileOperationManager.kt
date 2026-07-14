package com.fam4k007.videoplayer.utils

import android.content.Context
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 文件操作管理器
 * 处理文件/文件夹的删除、重命名、复制等操作
 */
object FileOperationManager {
    
    /**
     * 删除文件或文件夹
     * @param path 文件或文件夹路径
     * @param isFolder 是否是文件夹
     * @return 是否成功
     */
    suspend fun delete(context: Context, path: String, isFolder: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (!file.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext false
                }
                
                val result = if (isFolder) {
                    // 递归删除文件夹
                    file.deleteRecursively()
                } else {
                    // 删除单个文件
                    file.delete()
                }
                
                if (result) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Deleted successfully", Toast.LENGTH_SHORT).show()
                    }
                    // 通知系统媒体库更新
                    notifyMediaStore(context, path)
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Delete failed, possibly no permission", Toast.LENGTH_SHORT).show()
                    }
                }
                
                result
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                false
            }
        }
    }
    
    /**
     * 重命名文件或文件夹
     * @param oldPath 原路径
     * @param newName 新名称（不包含路径）
     * @return 新路径，失败返回null
     */
    suspend fun rename(context: Context, oldPath: String, newName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val oldFile = File(oldPath)
                if (!oldFile.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext null
                }
                
                // 构建新路径
                val parentDir = oldFile.parentFile
                val newFile = File(parentDir, newName)
                
                // 检查新文件是否已存在
                if (newFile.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Target file already exists", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext null
                }
                
                // 重命名
                val result = oldFile.renameTo(newFile)
                
                if (result) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Renamed successfully", Toast.LENGTH_SHORT).show()
                    }
                    // 通知系统媒体库更新
                    notifyMediaStore(context, oldPath)
                    notifyMediaStore(context, newFile.absolutePath)
                    newFile.absolutePath
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Rename failed, possibly no permission", Toast.LENGTH_SHORT).show()
                    }
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Rename failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                null
            }
        }
    }
    
    /**
     * 复制文件或文件夹
     * @param sourcePath 源路径
     * @param destPath 目标路径（完整路径，包含文件名）
     * @param isFolder 是否是文件夹
     * @return 是否成功
     */
    suspend fun copy(context: Context, sourcePath: String, destPath: String, isFolder: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sourceFile = File(sourcePath)
                if (!sourceFile.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Source file does not exist", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext false
                }
                
                val destFile = File(destPath)
                
                // 确保目标目录存在
                destFile.parentFile?.mkdirs()
                
                // 检查目标文件是否已存在
                if (destFile.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Target file already exists", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext false
                }
                
                val result = if (isFolder) {
                    // 递归复制文件夹
                    copyDirectory(sourceFile, destFile)
                } else {
                    // 复制单个文件
                    copyFile(sourceFile, destFile)
                }
                
                if (result) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Copied successfully", Toast.LENGTH_SHORT).show()
                    }
                    // 通知系统媒体库更新
                    notifyMediaStore(context, destPath)
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Copy failed", Toast.LENGTH_SHORT).show()
                    }
                }
                
                result
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Copy failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                false
            }
        }
    }
    
    /**
     * 复制单个文件
     */
    private fun copyFile(source: File, dest: File): Boolean {
        return try {
            FileInputStream(source).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 递归复制文件夹
     */
    private fun copyDirectory(source: File, dest: File): Boolean {
        return try {
            // 创建目标文件夹
            if (!dest.exists()) {
                dest.mkdirs()
            }
            
            // 复制所有子文件和文件夹
            source.listFiles()?.forEach { file ->
                val destFile = File(dest, file.name)
                if (file.isDirectory) {
                    copyDirectory(file, destFile)
                } else {
                    copyFile(file, destFile)
                }
            }
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 通知系统媒体库更新
     */
    private fun notifyMediaStore(context: Context, path: String) {
        try {
            val file = File(path)
            if (file.exists()) {
                // 文件存在，扫描
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(path),
                    null,
                    null
                )
            } else {
                // 文件已删除，从媒体库中移除
                context.contentResolver.delete(
                    android.provider.MediaStore.Files.getContentUri("external"),
                    "${android.provider.MediaStore.Files.FileColumns.DATA}=?",
                    arrayOf(path)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
