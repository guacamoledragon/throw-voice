package tech.gdragon.mp3

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3AudioHeader
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import java.io.File

fun main() {

//  val testFile = File("data\\recordings\\01G6BDR0C7HFMMVD61H4Y3RB23.mp3")
//  val testFile = File("data\\recordings\\01J0AQCG672626Y7EG22TASF8D.mp3")
  val testFile = File("data\\recordings\\01HQ2291CD73M03VSS6XN9GM08.mp3")
  val f: MP3File = AudioFileIO.read(testFile) as MP3File
  val audioHeader: MP3AudioHeader = f.mP3AudioHeader
  println(audioHeader.trackLength)
  println(audioHeader.sampleRateAsNumber)
  println(audioHeader.channels)
  println(audioHeader.isVariableBitRate)

  println(f.hasID3v1Tag())
  println(f.hasID3v2Tag())

  val tag = f.tagAndConvertOrCreateAndSetDefault
  tag.setField(FieldKey.COMMENT, "Speakers: speaker")
  f.commit()
}
