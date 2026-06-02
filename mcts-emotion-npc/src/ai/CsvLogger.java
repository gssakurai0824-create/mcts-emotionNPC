package ai;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CsvLogger {

    private static final String FRAME_LOG_PATH;
    private static final String ROUND_LOG_PATH;

    private static PrintWriter frameLogWriter;
    private static PrintWriter roundLogWriter;

    private static boolean frameHeaderWritten = false;
    private static boolean roundHeaderWritten = false;

    static {
        String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        );

        new java.io.File("log").mkdirs();

        FRAME_LOG_PATH = "log/frames_" + timestamp + ".csv";
        ROUND_LOG_PATH = "log/rounds_" + timestamp + ".csv";

        try {
            frameLogWriter = new PrintWriter(new FileWriter(FRAME_LOG_PATH, true));
            roundLogWriter = new PrintWriter(new FileWriter(ROUND_LOG_PATH, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("[CSV] CsvLogger LOADED (with mkdirs + writerOK print)");
    }

    private static void writeFrameHeaderIfNeeded() {
        if (!frameHeaderWritten && frameLogWriter != null) {
            frameLogWriter.println(
                "exp_id,match_id,round,frame," +
                "p1_ai,p2_ai," +
                "side," +
                "p1_hp,p2_hp,p1_energy,p2_energy," +
                "p1_action,p2_action," +
                "joy,anger,fear,sadness," +
                "my_remain_hp,opp_remain_hp,delta_my,delta_opp,air_fa_streak," +
                "atk,def,eva,app,dist,aa"
            );
            frameLogWriter.flush();
            frameHeaderWritten = true;
        }
    }

    // 呼び出し側が「クラスロード」で初期化されるので、空でOK
    public static void ensureInitialized() {
    }

    public static void logFrame(
            String expId, int matchId, int round, int frame,
            String p1Ai, String p2Ai,
            String side,
            int p1Hp, int p2Hp,
            int p1Energy, int p2Energy,
            String p1Action, String p2Action,
            double joy, double anger, double fear, double sadness,
            int myRemainHp, int oppRemainHp,
            int deltaMy, int deltaOpp,
            int airFaStreak,
            double atk, double def, double eva, double app, double dist, double aa
    ) {
        if (frameLogWriter == null) return;

        writeFrameHeaderIfNeeded();

        frameLogWriter.printf(
            "%s,%d,%d,%d,%s,%s,%s,%d,%d,%d,%d,%s,%s,%.3f,%.3f,%.3f,%.3f," +
            "%d,%d,%d,%d,%d," +
            "%.3f,%.3f,%.3f,%.3f,%.3f,%.3f%n",
            expId, matchId, round, frame,
            p1Ai, p2Ai,
            side,
            p1Hp, p2Hp,
            p1Energy, p2Energy,
            p1Action, p2Action,
            joy, anger, fear, sadness,
            myRemainHp, oppRemainHp,
            deltaMy, deltaOpp,
            airFaStreak,
            atk, def, eva, app, dist, aa
        );

        frameLogWriter.flush();
    }

    private static void writeRoundHeaderIfNeeded() {
        if (!roundHeaderWritten && roundLogWriter != null) {
            roundLogWriter.println(
                "exp_id,match_id,round," +
                "p1_ai,p2_ai," +
                "p1_hp,p2_hp," +
                "result,frames"
            );
            roundLogWriter.flush();
            roundHeaderWritten = true;
        }
    }

    public static void logRoundResult(
            String expId,
            int matchId,
            int round,
            String p1Ai,
            String p2Ai,
            int p1Hp,
            int p2Hp,
            int result,
            int frames
    ) {
        if (roundLogWriter == null) return;

        writeRoundHeaderIfNeeded();

        roundLogWriter.printf(
            "%s,%d,%d,%s,%s,%d,%d,%d,%d%n",
            expId,
            matchId,
            round,
            p1Ai,
            p2Ai,
            p1Hp,
            p2Hp,
            result,
            frames
        );

        roundLogWriter.flush();
    }

    public static void close() {
        if (frameLogWriter != null) {
            frameLogWriter.flush();
            frameLogWriter.close();
        }
        if (roundLogWriter != null) {
            roundLogWriter.flush();
            roundLogWriter.close();
        }
    }
}
