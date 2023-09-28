package tech.gdragon.utils

import java.net.URL
import java.net.URLClassLoader

class IsolatedClassLoader(urls: Array<out URL>?) : URLClassLoader(urls) {
  @Throws(ClassNotFoundException::class)
  override fun loadClass(name: String): Class<*> {
    // Check for already loaded classes first
    return findLoadedClass(name)
      ?: try {
        // Try to load it locally first
        findClass(name)
      } catch (e: ClassNotFoundException) {
        // Defer to the parent classloader
        super.loadClass(name)
      }
  }
}
