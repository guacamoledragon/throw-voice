/**
 * Created by Austin on 10/9/2016.
 */

import net.dv8tion.jda.audio.AudioReceiveHandler;
import net.dv8tion.jda.audio.CombinedAudio;
import net.dv8tion.jda.audio.UserAudio;
import net.dv8tion.jda.entities.User;
import java.util.concurrent.ConcurrentLinkedQueue;


public class RecordAudio implements AudioReceiveHandler
{
    double volume = 1.0;
    ConcurrentLinkedQueue<byte[]> data = new ConcurrentLinkedQueue<>();

    @Override
    public boolean canReceiveCombined()
    {
        return true;
    }

    @Override
    public boolean canReceiveUser()
    {
        return false;
    }

    @Override
    public void handleCombinedAudio(CombinedAudio combinedAudio)
    {
        data.add(combinedAudio.getAudioData(volume));
    }

    @Override
    public void handleUserAudio(UserAudio userAudio)
    {

    }

    @Override
    public void handleUserTalking(User user, boolean talking)
    {

    }
}