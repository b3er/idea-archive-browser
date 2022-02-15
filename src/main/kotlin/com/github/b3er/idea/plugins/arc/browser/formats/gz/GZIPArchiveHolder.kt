package com.github.b3er.idea.plugins.arc.browser.formats.gz

import java.io.File

class GZIPArchiveHolder(file: File) {
  val archive: GZipFile = GZipFile(file)
}