package tech.gdragon.listener.rx

import io.reactivex.Observable
import net.dv8tion.jda.core.audio.AudioReceiveHandler
import net.dv8tion.jda.core.audio.CombinedAudio
import net.dv8tion.jda.core.audio.UserAudio
import net.dv8tion.jda.core.managers.AudioManager

fun audioReceiveObservable(audioManager: AudioManager): Observable<CombinedAudio> {
  return Observable.create { subscriber ->
    object : AudioReceiveHandler {
      init {
        println("init observable")
        audioManager.setReceivingHandler(this)
      }

      override fun canReceiveUser(): Boolean = false

      // NOTE: Each segment of audio data is 3840 bytes long
      // NOTE: 48KHz 16bit stereo signed BigEndian PCM
      override fun handleCombinedAudio(combinedAudio: CombinedAudio) {
        if (subscriber.isDisposed) {
          println("subscriber.isDisposed = ${subscriber.isDisposed}")
        } else {
          subscriber.onNext(combinedAudio)
        }
      }

      override fun handleUserAudio(userAudio: UserAudio?) {
        TODO("Not supported.")
      }

      override fun canReceiveCombined(): Boolean = true

    }
  }
}
