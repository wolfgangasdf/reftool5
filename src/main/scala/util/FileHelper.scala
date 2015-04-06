package util

/**
 * Created with IntelliJ IDEA.
 * User: wolle
 * Date: 14.10.2012
 * Time: 18:47
 * To change this template use File | Settings | File Templates.
 */
import java.io._

object FileHelper {

  def write(file: File, text : String) : Unit = {
    val fw = new FileWriter(file)
    try{ fw.write(text) }
    finally{ fw.close() }
  }
  def foreachLine(file: File, proc : String=>Unit) : Unit = {
    val br = new BufferedReader(new FileReader(file))
    try{ while(br.ready) proc(br.readLine) }
    finally{ br.close() }
  }
  def deleteAll(file: File) : Unit = {
    def deleteFile(dfile : File) : Unit = {
      if(dfile.isDirectory) {
        println("deleting " + dfile + " recursively")
        dfile.listFiles.foreach{ f => deleteFile(f) }
      }
      dfile.delete
    }
    deleteFile(file)
  }
  def splitName(ff: File) = {
    val f = ff.getName
    val extension = f.substring(f.lastIndexOf('.'))
    val name = f.substring(0, f.lastIndexOf('.'))
    (name, extension)
  }
}

