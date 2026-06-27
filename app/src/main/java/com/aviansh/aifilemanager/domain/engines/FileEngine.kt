package com.aviansh.aifilemanager.domain.engines

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream

object FileEngine {
    fun copyFile(source:File, dest:File){
        val inputStream = FileInputStream(dest)
        val outputStream = FileOutputStream(source)
        inputStream.copyTo(outputStream)
    }

    fun moveFile(source:File, dest:File){
        if(dest.exists()){
            dest.delete()
        }
        dest.createNewFile()

        val inputStream = FileInputStream(dest)
        val outputStream = FileOutputStream(source)
        inputStream.copyTo(outputStream)
        source.delete()
    }
    fun createFile(path: String, tmpFile: File, overwrite: Boolean): File{
        val sourceFile = File(path)
        if(sourceFile.exists() && !overwrite){
            throw FileAlreadyExistsException(sourceFile)
        }
        copyFile(sourceFile, tmpFile)
        return sourceFile
    }

    fun updateFile(path: String, tmpFile: File, create: Boolean=false): File{
        val sourceFile = File(path)
        if(!sourceFile.exists() && !create){
            throw FileNotFoundException()
        }
        copyFile(sourceFile, tmpFile)
        return sourceFile
    }

    fun readFile(path: String): File{
        return File(path)
    }

    fun listDir(basePath: String): List<File>{
        val dir = File(basePath);
        if(!dir.exists()){
            throw FileNotFoundException("Directory Not Found")
        }
        val files = dir.listFiles()
        if (files != null) {
            return files.toList()
        }

        return emptyList()
    }
}