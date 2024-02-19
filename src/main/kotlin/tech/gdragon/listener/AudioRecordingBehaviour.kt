package tech.gdragon.listener

interface AudioRecorder {
  fun isAfk(userCount: Int): Boolean
  fun checkRecordingLimit(bytes: ByteArray)
  fun uploadRecording(recording: File, voiceChannel: AudioChannel, messageChannel: MessageChannel)
}
