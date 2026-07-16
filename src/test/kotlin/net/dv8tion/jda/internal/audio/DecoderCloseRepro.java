package net.dv8tion.jda.internal.audio;

import net.dv8tion.jda.api.audio.AudioNatives;

/**
 * Forked-JVM repro for the Decoder close/decode race that crashes production
 * (SIGSEGV in opus_decode/opus_decoder_get_nb_samples, see
 * .agent/plans/production-stability-debugging.md).
 *
 * Lives in JDA's package because Decoder's constructor and close() are protected.
 * Run by DecoderRaceTest in a separate JVM: with stock JDA 6.4.2 the second
 * decode dies with SIGSEGV; with the patched Decoder it prints "OK afterClose=null"
 * and exits 0.
 */
public class DecoderCloseRepro {
    public static void main(String[] args) {
        if (!AudioNatives.ensureOpus()) {
            System.out.println("SANITY_FAIL: opus natives unavailable");
            System.exit(3);
        }

        Decoder decoder = new Decoder(1);

        // Packet-loss decode on an open decoder must work — proves the setup is valid.
        short[] beforeClose = decoder.decodeFromOpus(null);
        if (beforeClose == null) {
            System.out.println("SANITY_FAIL: decode on open decoder returned null");
            System.exit(2);
        }

        decoder.close();

        // Stock JDA: opusDecoder field is now null and this call hands it straight
        // to native opus_decode -> SIGSEGV (si_addr 0xc), same as production.
        short[] afterClose = decoder.decodeFromOpus(null);

        System.out.println("OK afterClose=" + (afterClose == null ? "null" : "pcm"));
    }
}
