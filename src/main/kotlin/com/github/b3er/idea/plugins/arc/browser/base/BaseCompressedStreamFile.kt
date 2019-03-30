package com.github.b3er.idea.plugins.arc.browser.base

import java.io.InputStream

interface BaseCompressedStreamFile {
    val name: String
    val length: Long
    val timestamp: Long
    fun inputStream(): InputStream
}