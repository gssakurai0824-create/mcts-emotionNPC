package ai;

import struct.FrameData;
import struct.GameData;
import struct.Key;
import struct.CharacterData;
import enumerate.Action; //現在行動(AIR_FAなど)を見るために追加

/**
 * MctsAi23i を継承して、簡易感情モデルを上乗せした版。
 *思考（MCTS）の本体は MctsAi23i に任せる
 *HP変化や連続被弾・画面端拘束から Joy / Anger / Fear / Sadnessを内部で更新
 *input() で元のキー入力を感情に応じて少しだけ調整して返す
 */
public class MctsAi23iEmotion extends MctsAi23i {//MctsAi21iを継承

    private static final String VERSION = "EmotionAI_20251126_v2";

    static {
        System.out.println("[EmotionAI] Class loaded. VERSION = " + VERSION);
        System.out.println(
            "[EmotionAI] loaded from = " +
            MctsAi23iEmotion.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
        );
    }

    //実験ID・試合ID管理
    private static int GLOBAL_MATCH_ID = 0;//全試合で共通のカウンタ
    private String expId = "exp01";//実験ID（必要に応じて手動で変える）
    
    //基本設定・定数
    private static final int MAX_HP = 1000;//実験で使う最大HP
    private static final double FPS = 60.0;//DARE/FightingICEは60fps

    //感情ごとの「半減するまでの時間（秒）」
    private static final double HALF_LIFE_JOY     = 3.0;
    private static final double HALF_LIFE_ANGER   = 5.0;
    private static final double HALF_LIFE_FEAR    = 4.0;
    private static final double HALF_LIFE_SADNESS = 6.0;

    // 半減時間から 1フレームあたりの減衰係数を計算
    //60*ⅹフレーム後に、値がちょうど 0.5（半分）になるようにしたい
    private static final double DECAY_JOY     = Math.pow(0.5, 1.0 / (HALF_LIFE_JOY     * FPS));//1フレームの減衰係数 = 0.5^(1/180（3F）) = 約0.99616
    private static final double DECAY_ANGER   = Math.pow(0.5, 1.0 / (HALF_LIFE_ANGER   * FPS));
    private static final double DECAY_FEAR    = Math.pow(0.5, 1.0 / (HALF_LIFE_FEAR    * FPS));
    private static final double DECAY_SADNESS = Math.pow(0.5, 1.0 / (HALF_LIFE_SADNESS * FPS));

    //画面端判定用（おおよその値）
    private static final int STAGE_WIDTH          = 960;//Window960x640に合わせる
    private static final int CORNER_MARGIN_X      = 80;//左右80px以内を画面端付近とみなす
    private static final int CORNER_FRAMES_THRESH = 60;//1秒以上拘束されると Fear/Sadness増加

    //動きの滑らかさ
    private static final int MOVE_SMOOTH_FRAMES = 3;//方向が変わるときの慣性フレーム数

    //========　内部状態　========

    //自分がP1かP2か
    private boolean myPlayerNumber;

    //このAIインスタンスに割り当てる試合ID
    private int matchId;

    //ラウンドとフレームカウンタ（ログ用）
    private int currentRound = 0;
    private int frameInRound = 0;

    private boolean roundResultLogged = false;//ラウンド結果を1回だけ書くため

    // 感情状態（0〜100の範囲で扱う）
    private double joy;
    private double anger;
    private double fear;
    private double sadness;

    // 戦術パラメータ（0.0〜1.0）初期値
    private double paramAttack   = 0.5;
    private double paramDefense  = 0.5;
    private double paramEvasion  = 0.5;
    private double paramApproach = 0.5;
    private double paramDistance = 0.5;
    private double paramAntiAir  = 0.5;
    
    // Fear 専用モード
    private boolean fearGuardMode = false;//恐怖モードが発動しているかどうか
    private int fearGuardTimer    = 0;//恐怖モードの 残りフレーム数をカウントダウンするための変数
    private static final int FEAR_GUARD_THRESHOLD = 70;//モード発動判定に入るための閾値
    private static final int FEAR_GUARD_DURATION  = 30;//恐怖モードが どれだけ持続するか 30フレーム＝0.5秒

    // 前フレームのHP
    private int lastMyHp  = -1;//被ダメージ計算
    private int lastOppHp = -1;//与ダメージ計算

    // --- HP以外の誘発条件用 ---
    // 連続被弾回数・連続与ダメ回数
    private int consecutiveMyHit  = 0;//自分が連続で被弾した回数
    private int consecutiveOppHit = 0;//相手が連続で被弾した回数

    // 画面端拘束フレーム数(初期状態)
    private int myCornerFrames  = 0;
    private int oppCornerFrames = 0;

    // 現在行動と AIR_FA 連打検出
    private Action lastMyAction = Action.STAND;//STANDで行動を初期化
    private int airFaStreak     = 0;// AIR_FA がどれだけ続いているか

    // 出力キーの履歴（滑らかさ用）スムージング処理に利用する。
    private Key lastOutputKey     = new Key();
    private int moveSmoothCounter = 0;//スムージング処理が完了し０になったら行動可

    public MctsAi23iEmotion() {
        super();// 親クラス (MctsAi23i) の初期化
        resetEmotion();// 感情値・状態カウンタなどを初期状態にリセット
    }

    /** 感情・戦術パラメータ・HP基準をリセット */
    private void resetEmotion() {
        //感情値リセット
        joy = 0.0;
        anger = 0.0;
        fear = 0.0;
        sadness = 0.0;

        //戦術パラメータリセット
        paramAttack   = 0.5;
        paramDefense  = 0.5;
        paramEvasion  = 0.5;
        paramApproach = 0.5;
        paramDistance = 0.5;
        paramAntiAir  = 0.5;

        //前のHPリセット
        lastMyHp  = -1;
        lastOppHp = -1;

        //Fear専用モードリセット
        fearGuardMode  = false;
        fearGuardTimer = 0;

        consecutiveMyHit  = 0;//自分が連続で被弾した回数
        consecutiveOppHit = 0;//相手が連続で被弾した回数
        myCornerFrames    = 0;
        oppCornerFrames   = 0;

        lastMyAction = Action.STAND;
        airFaStreak  = 0;

        lastOutputKey = new Key();
        moveSmoothCounter = 0;
    }

    //感情値から戦術パラメータを更新する
    private void updateTacticalParams() {
        //0〜100の感情値を3段階(0,1,2)にわける
        int joyLevel      = getLevel(joy);
        int angerLevel    = getLevel(anger);
        int fearLevel     = getLevel(fear);
        int sadnessLevel  = getLevel(sadness);

        //中立状態にリセット
        paramAttack   = 0.5;
        paramDefense  = 0.5;
        paramEvasion  = 0.5;
        paramApproach = 0.5;
        paramDistance = 0.5;
        paramAntiAir  = 0.5;

        //Joy
        if (joyLevel == 0) {//平穏
            paramAttack   += 0.05;
            paramApproach += 0.05;
            paramAntiAir  += 0.05;
        } else if (joyLevel == 1) {//喜び
            paramAttack   += 0.10;
            paramApproach += 0.10;
            paramAntiAir  += 0.10;
        } else if (joyLevel == 2) {//歓喜
            paramAttack   += 0.20;
            paramApproach += 0.20;
            paramDistance -= 0.05;
            paramAntiAir  += 0.15;
        }

        //Anger
        if (angerLevel == 0) {//イライラ
            paramAttack  += 0.05;
            paramDefense -= 0.05;
            paramAntiAir -= 0.05;
        } else if (angerLevel == 1) {//怒り
            paramAttack  += 0.25;
            paramDefense -= 0.10;
            paramDistance -= 0.05;
            paramAntiAir -= 0.10;
        } else if (angerLevel == 2) {//激怒
            paramAttack  += 0.40;
            paramDefense -= 0.30;
            paramEvasion -= 0.10;
            paramApproach += 0.20;
            paramDistance -= 0.10;
            paramAntiAir -= 0.15;
        }

        //Fear
        if (fearLevel == 0) {//不安
            paramAttack   -= 0.05;
            paramDefense  += 0.05;
            paramEvasion  += 0.10;
            paramApproach -= 0.05;
            paramDistance += 0.05;
            paramAntiAir  += 0.05;
        } else if (fearLevel == 1) {//恐れ
            paramAttack   -= 0.10;
            paramDefense  += 0.10;
            paramEvasion  += 0.20;
            paramApproach -= 0.10;
            paramDistance += 0.10;
            paramAntiAir  += 0.60;
        } else if (fearLevel == 2) {//恐怖
            paramAttack   -= 0.20;
            paramDefense  += 0.15;
            paramEvasion  += 0.30;
            paramApproach -= 0.20;
            paramDistance += 0.20;
            paramAntiAir  += 0.55;
        }

        //Sadness
        if (sadnessLevel == 0) {//哀愁
            paramAttack   -= 0.05;
            paramDefense  += 0.10;
            paramDistance += 0.10;
            paramAntiAir  -= 0.05;
        } else if (sadnessLevel == 1) {//悲しみ
            paramAttack   -= 0.10;
            paramDefense  += 0.20;
            paramApproach -= 0.05;
            paramDistance += 0.20;
            paramAntiAir  -= 0.10;
        } else if (sadnessLevel == 2) {//悲嘆
            paramAttack   -= 0.20;
            paramDefense  += 0.30;
            paramApproach -= 0.10;
            paramDistance += 0.30;
            paramAntiAir  -= 0.10;
        }

        //戦術パラメータの範囲調整
        //0.0～1.0の範囲を超えないよう調整
        paramAttack   = clamp01(paramAttack);
        paramDefense  = clamp01(paramDefense);
        paramEvasion  = clamp01(paramEvasion);
        paramApproach = clamp01(paramApproach);
        paramDistance = clamp01(paramDistance);
        paramAntiAir  = clamp01(paramAntiAir);

        //デバッグ用、感情の変化によって戦術パラメータがどの程度変化したかを小数点2桁で出力する
        System.out.println(
            String.format("[Param] atk=%.2f def=%.2f eva=%.2f app=%.2f dist=%.2f aa=%.2f",
                paramAttack, paramDefense, paramEvasion, paramApproach, paramDistance, paramAntiAir)
        );
    }

    //0〜100 の感情値を 3段階(0=弱, 1=中, 2=強)に変換
    private int getLevel(double v) {
        if (v < 50.0) return 0;//弱い強度
        if (v < 90.0) return 1;//基本感情
        return 2;//強い強度
    }

    //0.0〜1.0 の範囲に制限
    private double clamp01(double v) {
        if (v < 0.0) return 0.0;//vが0.0より小さい場合は、0.0を返す
        if (v > 1.0) return 1.0;//vが1.0より大きい場合は、1.0を返す
        return v;
    }

    //計算結果がmin〜maxの範囲を超えてしまった場合、範囲内に収まるよう調整して返す
    //実装では感情値の上限を 100 に設定しているが、想定以上の増減防止、拡張性のため
    private double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    @Override
    public int initialize(GameData gameData, boolean playerNumber) {//AIを初期化するメソッド
        this.myPlayerNumber = playerNumber;//自分は1Pか2Pか
        
        CsvLogger.ensureInitialized();
        
        //試合IDを採番（P1だけ）
        if (playerNumber) {
        GLOBAL_MATCH_ID++;
        }

        this.matchId = GLOBAL_MATCH_ID;
        //ラウンド・フレームをリセット
        currentRound = 0;
        frameInRound = 0;

        resetEmotion();
        System.out.println(
            "[EmotionAI] initialize. VERSION = " + VERSION + " player=" + (playerNumber ? "P1" : "P2")//コンソールにいつ初期化されたか、どのバージョンの AI か、自分が P1/P2 のどちらかを出力する
        );
        return super.initialize(gameData, playerNumber);//MctsAi23iの正常動作のための初期化処理
    }

    /**
     * フレームごとのゲーム情報を受け取り，AI 内部状態を更新するメソッド。
     * 
    */
    public void getInformation(FrameData frameData, boolean isControl) {
        //まず元のMctsAi23iに情報を渡す
        //AI全体必要とするフレーム情報を渡す
        try {
        super.getInformation(frameData, isControl);
        } catch (Exception e) {//エラーが起きても表示だけして、処理は止めない
            System.err.println("[EmotionAI] Parent AI crashed in getInformation: " + e.getMessage());
        }
        //次に本研究で追加した感情モデルを更新する
        updateEmotion(frameData);
    }

    /**
     * フレーム情報から HP変化・位置・連続被弾などを取得し、Joy / Anger / Fear / Sadness の4感情を更新する
     *  感情値は半減時間に基づいて毎フレーム自然減衰する。frameData が空の場合は更新せず終了する。
     * AIInterface では getInformation が抽象メソッドとして定義されているため、
     * 本クラスでも必ずこのメソッドを実装しなければならない。
     * その際、上書きであることを明示するために @Override を付けている。
     */
    private void updateEmotion(FrameData frameData) {
        if (frameData == null || frameData.getEmptyFlag()) {
            return;//ゲーム開始時などのフレーム情報が無い場合、感情更新はスキップ＝バグ防止
        }

        //自分がP1かP2かを確認して、相手のプレイヤー番号を決める
        CharacterData my  = frameData.getCharacter(myPlayerNumber);

        CharacterData opp;
        if (myPlayerNumber) {//自分がP1のとき
            opp = frameData.getCharacter(false);//相手はP2
        } else {//自分がP2のとき
    opp = frameData.getCharacter(true);//相手はP1
}

        //残りHP（0〜1000）に変換
        int myHpRaw  = my.getHp();
        int oppHpRaw = opp.getHp();

        int myRemainHP  = MAX_HP + myHpRaw;
        int oppRemainHP = MAX_HP + oppHpRaw;

        //試合開始1フレーム目は、感情を更新せず、HPの初期値だけを保存しておくための安全処理
        if (lastMyHp < 0) {
            lastMyHp  = myHpRaw;
            lastOppHp = oppHpRaw;
            return;
        }

        //前フレームより
        int deltaMy  = myHpRaw  - lastMyHp;//マイナスなら被弾
        int deltaOpp = oppHpRaw - lastOppHp;//マイナスなら与ダメ

        //今このラウンドで有利か不利か
        int hpDiff = myRemainHP - oppRemainHP;//プラスなら有利、マイナスなら不利

        //各感情は設定した半減時間に基づき徐々に減衰する
        joy     *= DECAY_JOY;
        anger   *= DECAY_ANGER;
        fear    *= DECAY_FEAR;
        sadness *= DECAY_SADNESS;

        //============ 感情値変化ルール ==============
        //ダメージ由来の感情変化
        // 与ダメージ ： Joy,Angerを増加、Sadnessを減少
        if (deltaOpp < 0) {
            int dmg = -deltaOpp;
            joy     += dmg * 0.5;
            anger   += dmg * 0.1;
            sadness -= dmg * 0.3;
        }

        // 被ダメージ ： Anger、Fearを増加、Joyを減少
        if (deltaMy < 0) {
            int dmg = -deltaMy;
            anger   += dmg * 0.4;
            fear    += dmg * 0.3;
            joy     -= dmg * 0.2;
        }

        //HP差の感情変化
        if (hpDiff > 0) {
            //有利 ： Joy増加、Sadness減少（0.1＝大きく加算すると感情値が暴走する）
            joy     += 0.05 * hpDiff * 0.1;
            sadness -= 0.02 * hpDiff * 0.1;
        } else if (hpDiff < 0) {
            // 不利：Sadness、Fear 増加
            double diff = -hpDiff;
            sadness += 0.05 * diff * 0.1;
            fear    += 0.02 * diff * 0.1;
        }

        //連続被弾・連続与ダメの感情変化
        //被ダメージが発生した場合（自分のHPが減った場合）
        if (deltaMy < 0) {
            consecutiveMyHit++;//自分が被弾した回数を+1
            consecutiveOppHit = 0;//相手への連続与ダメージカウンタは無視する
        //与ダメージが発生した場合（相手のHPが減った場合）
        } else if (deltaOpp < 0) {
            consecutiveOppHit++;
            consecutiveMyHit = 0;
        //どちらのHPにも変化がないフレーム（攻防が発生していない）
        // しばらくダメージが発生していない場合は、「連続で攻撃された」という状態が自然に薄れていくように1ずつ減らす
        } else {
            if (consecutiveMyHit > 0)  consecutiveMyHit--;//連続被弾状態が継続していないので徐々に減衰させる
            if (consecutiveOppHit > 0) consecutiveOppHit--;//連続与ダメ状態も継続していないので徐々に減衰させる
        }

        //自分が3回以上連続で攻撃を受けた場合
        if (consecutiveMyHit >= 3) {
            int extra = consecutiveMyHit - 2; //3回目以降の回数（3回なら1、4回なら2〜のように強さを表す値）
            fear    += 5.0 * extra;
            sadness += 3.0 * extra;
        }

        if (consecutiveOppHit >= 3) {
            int extra = consecutiveOppHit - 2;
            joy   += 4.0 * extra;
            anger += 2.0 * extra;
        }

        //画面端拘束
        int myX  = my.getX();
        int oppX = opp.getX();

       boolean myInCorner;

        if (myX < CORNER_MARGIN_X) {//左端（0〜80px）に入っている場合
            myInCorner = true;
        } else if (myX > STAGE_WIDTH - CORNER_MARGIN_X) {//右端（880〜960px）に入っている場合
            myInCorner = true;
        } else {//どちらの端にも該当しない（中央）
            myInCorner = false;
        }

        boolean oppInCorner;

        if (oppX < CORNER_MARGIN_X) {
        // 相手が左端付近にいる
            oppInCorner = true;
        } else if (oppX > STAGE_WIDTH - CORNER_MARGIN_X) {
        // 相手が右端付近にいる
            oppInCorner = true;
        } else {
        // 相手が画面中央付近にいる
            oppInCorner = false;
        }

        //自キャラが画面端ゾーンにいる場合myCornerFramesを1フレームずつ増加
        //画面端から離れた場合myCornerFramesを0にリセット
        if (myInCorner) {
            myCornerFrames++;
        } else {
            myCornerFrames = 0;
        }

        if (oppInCorner) {
            oppCornerFrames++;
        } else {
            oppCornerFrames = 0;
        }

        if (myCornerFrames > CORNER_FRAMES_THRESH) {
            int extra = myCornerFrames - CORNER_FRAMES_THRESH;
            fear    += 0.5 * extra;
            sadness += 0.3 * extra;
        }

        if (oppCornerFrames > CORNER_FRAMES_THRESH) {
            int extra = oppCornerFrames - CORNER_FRAMES_THRESH;
            joy   += 0.4 * extra;
            anger += 0.2 * extra;
        }

        //AIR_FA連打の検出
        Action myAct = my.getAction();
        lastMyAction = myAct;

        if (myAct == Action.AIR_FA) {
            airFaStreak++;
        } else {
            if (airFaStreak > 0) {
                airFaStreak--;
            }
        }

        // AIR_FA を振り過ぎているととしてFearを上昇
        if (airFaStreak > 20) {
            fear += 0.5 * (airFaStreak - 20);
        }

        //感情値を0から100に収める
        joy     = clamp(joy, 0.0, 100.0);
        anger   = clamp(anger, 0.0, 100.0);
        fear    = clamp(fear,  0.0, 100.0);
        sadness = clamp(sadness, 0.0, 100.0);

        // 戦術パラメータを更新
        updateTacticalParams();

        if (this.getClass().getSimpleName().equals("MctsAi23iEmotion")) {

        //ここで1フレーム分のCSVログを書き出す
         try {
            int round = frameData.getRound();//現在ラウンド番号

            //ラウンドが変わったらフレームカウンタをリセット
            if (round != currentRound) {
                currentRound = round;
                frameInRound = 0;
                roundResultLogged = false;

                lastMyHp = -1;
                lastOppHp = -1;
                return;//次のフレームで初期HPを保存してから計算開始
            }

            int frameNumber = frameInRound;//このフレームの番号（0,1,2,...）

            CharacterData p1 = frameData.getCharacter(true);
            CharacterData p2 = frameData.getCharacter(false);

            int p1Hp = p1.getHp();
            int p2Hp = p2.getHp();
            int p1Energy = p1.getEnergy();
            int p2Energy = p2.getEnergy();

            String p1Action = p1.getAction().name();
            String p2Action = p2.getAction().name();

            String side = myPlayerNumber ? "P1" : "P2";

            String p1AiName;
            String p2AiName;
            if (myPlayerNumber) {
                // このAIがP1の場合
                p1AiName = "MctsAi23iEmotion";
                p2AiName = "MctsAi23i";
            } else {
                // このAIがP2の場合
                p1AiName = "MctsAi23i";
                p2AiName = "MctsAi23iEmotion";
            }

           CsvLogger.logFrame(
                expId,
                matchId,
                round,
                frameNumber,
                p1AiName,
                p2AiName,
                side,
                p1Hp,
                p2Hp,
                p1Energy,
                p2Energy,
                p1Action,
                p2Action,
                joy,
                anger,
                fear,
                sadness,
                myRemainHP,
                oppRemainHP,
                deltaMy,
                deltaOpp,
                airFaStreak,
                paramAttack,
                paramDefense,
                paramEvasion,
                paramApproach,
                paramDistance,
                paramAntiAir
            );

            // ===== Round log（ラウンド終了時に1回だけ書く）=====
            boolean roundEnd =
                    (frameData.getRemainingTime() <= 0) ||
                    (p1Hp <= 0) ||
                    (p2Hp <= 0);

            if (roundEnd && myPlayerNumber && !roundResultLogged) {
                roundResultLogged = true;

                int result;
                if (p1Hp > p2Hp) result = 1;
                else if (p2Hp > p1Hp) result = -1;
                else result = 0;

                CsvLogger.logRoundResult(
                    expId,
                    matchId,
                    round,
                    p1AiName,
                    p2AiName,
                    p1Hp,
                    p2Hp,
                    result,
                    frameInRound
                );
            }
            frameInRound++;

        } catch (Exception e) {//ログ出力でエラーが出てもゲーム自体は止まらないようにする
            e.printStackTrace();
        }
    }

        // ログは1行に要約（コンソール用）
        System.out.println(
            "[Emotion] myRemainHP=" + myRemainHP +
            " oppRemainHP=" + oppRemainHP +
            " dMy=" + deltaMy +
            " dOpp=" + deltaOpp +
            " J=" + String.format("%.1f", joy) +
            " A=" + String.format("%.1f", anger) +
            " F=" + String.format("%.1f", fear) +
            " S=" + String.format("%.1f", sadness) +
            " airFA=" + airFaStreak
        );

        // 次フレーム用に記録
        lastMyHp  = myHpRaw;
        lastOppHp = oppHpRaw;

    }



    /**
     *元の MctsAi23iのinput()を呼び出し、
     *そのキーを感情に応じて少しだけ調整して返す
     */
    @Override
    public Key input() {
        //MctsAi23iが決めたキー入力を取得する
        Key baseKey;

        try {
            baseKey = super.input();
        } catch (Exception e) {
            System.err.println("[EmotionAI] Parent AI crashed in input: " + e.getMessage());
        //エラー時は「何もしない」キーをベースにして、棒立ちでもログだけは残す
        baseKey = new Key(); 
        }

                Key result = new Key();//新しい Key オブジェクトを用意し、
                copyKey(baseKey, result); //baseKey の内容（押されているボタン情報）をコピーしてから、このあと感情モデルに基づいて微調整を行う


        //===== 戦術パラメータ =====
        //攻撃性
        boolean anyAttack = false;

        if (result.A) {
            anyAttack = true;
        } else if (result.B) {
            anyAttack = true;
        } else if (result.C) {
            anyAttack = true;
        }
        double r = Math.random();

        if (anyAttack) {
            if (paramAttack < 0.5) {
                double cancelProb = (0.5 - paramAttack);//攻撃キャンセル確率0〜0.5
                if (r < cancelProb) {
                    result.A = false;
                    result.B = false;
                    result.C = false;
                }
            }
        } else {
            if (paramAttack > 0.5) {
                double addProb = (paramAttack - 0.5);//攻撃追加確率0〜0.5
                if (r < addProb) {
                    result.B = true;//一定確率でB攻撃を追加して攻撃的な行動を促す
                }
            }
        }

        //防御・回避パラメータ
        double defenseBias = Math.max(paramDefense, paramEvasion);//守りor回避、0.0〜1.0

        double defProb = (defenseBias - 0.5) * 0.3;//係数を下げてカクカク防止(0.3)
        if (!anyAttack && defProb > 0.0 && Math.random() < defProb) {
            if (myPlayerNumber) {//P1の後ろ方向
                result.L = true;
                result.R = false;
            } else {//P2の後ろ方向
                result.R = true;
                result.L = false;
            }

            if (paramEvasion > 0.5 && Math.random() < (paramEvasion - 0.5) * 0.3) {
                result.D = true;
                result.U = false;
            }
        }

        //接近パラメータ
        double appProb = (paramApproach - 0.5) * 0.3;
        if (!anyAttack && appProb > 0.0 && Math.random() < appProb) {
            if (myPlayerNumber) {
                result.R = true;//P1右側へ
                result.L = false;
            } else {
                result.L = true;//P2前左側へ
                result.R = false;
            }
        }

        //ジャンプ攻撃ループを弱める
        anyAttack = false;

        if (result.A) {
            anyAttack = true;
        } else if (result.B) {
            anyAttack = true;
        } else if (result.C) {
            anyAttack = true;
        }

        if (result.U && anyAttack && paramEvasion > paramAttack) {
            double cancelJumpProb = (paramEvasion - paramAttack); //0〜1を想定
            cancelJumpProb = clamp01(cancelJumpProb);//ジャンプを確実にキャンセル

            if (Math.random() < cancelJumpProb) {
                result.U = false;
                result.A = false;
                result.B = false;
                result.C = false;

                if (myPlayerNumber) {
                    result.L = true;
                    result.R = false;
                } else {
                    result.R = true;
                    result.L = false;
                }
            }
        }

        //AIR_FA由来のジャンプペナルティ
        //AIR_FA を振り続けているときほど、U＋攻撃をキャンセルしやすくする
        if (result.U && anyAttack) {
            double jumpPenalty = 0.2 + 0.02 * airFaStreak;//連続するほど上昇
            if (jumpPenalty > 0.8) jumpPenalty = 0.8;//上限 0.8

            if (Math.random() < jumpPenalty) {
                // ジャンプ攻撃をやめて、いったん地上で下がる
                result.U = false;
                result.A = false;
                result.B = false;
                result.C = false;

                if (myPlayerNumber) {
                    result.L = true;
                    result.R = false;
                } else {
                    result.R = true;
                    result.L = false;
                }
            }
        }

        //Valence/Arousalに基づく補正 
        double valence = joy - sadness;//快・不快の度合い[-100,100]
        double arousal = anger - fear;//興奮・抑制の度合い[-100,100]
        double valNorm = valence / 100.0;//正規化

        //Fear専用しゃがみガードモード
        if (fearGuardMode) {
            fearGuardTimer--;

            result.A = result.B = result.C = false;
            result.U = false;
            result.D = true;

            if (myPlayerNumber) {
                result.L = true;  result.R = false;//P1後ろ
            } else {
                result.R = true;  result.L = false;//P2後ろ
            }

            if (fearGuardTimer <= 0 || fear < FEAR_GUARD_THRESHOLD - 10) {
                fearGuardMode = false;
                //System.out.println("[MODE] FEAR GUARD MODE END");//デバッグ
            }

        } else if (!fearGuardMode && fear >= FEAR_GUARD_THRESHOLD && fear >= anger) {
            if (Math.random() < 0.3) {
                fearGuardMode  = true;
                fearGuardTimer = FEAR_GUARD_DURATION;
                //System.out.println("[MODE] FEAR GUARD MODE START");デバッグ
            }
        }

        // Anger が強いとき、何も攻撃してなければBを押しやすくする
        if (anger > 60 && anger >= fear) {
            if (!result.A && !result.B && !result.C) {
                if (Math.random() < 0.5) {
                    result.B = true;
                    //System.out.println("[Action] ANGER INFLUENCE: add B. anger=" + anger);デバッグ
                }
            }
        }

        if (valNorm < -0.5) {//劣勢・不快度が高い　→ 前進しづらくする（慎重）
            if (myPlayerNumber) {
                if (result.R && Math.random() < 0.7) result.R = false;
            } else {
                if (result.L && Math.random() < 0.7) result.L = false;
            }
        }

        if (valNorm > 0.5) {//優勢・快状態が高い → 前進を優先（積極的）
            if (myPlayerNumber) {
                result.R = true;
                result.L = false;
            } else {
                result.L = true;
                result.R = false;
            }
        }

        //方向キーに慣性を入れてカクつき防止
        Key finalKey = new Key();

        //攻撃ボタンは即時反映
        finalKey.A = result.A;
        finalKey.B = result.B;
        finalKey.C = result.C;

        //======== 方向入力スムージング処理 ========
        // 前フレームの方向入力と比較し、どれか1つでも異なれば「方向が変わった」と判定する。
        boolean dirChanged = false;

        if (result.U != lastOutputKey.U) dirChanged = true;//上方向が変わったか
        if (result.D != lastOutputKey.D) dirChanged = true;//下方向が変わったか
        if (result.L != lastOutputKey.L) dirChanged = true;//左方向が変わったか
        if (result.R != lastOutputKey.R) dirChanged = true;//右方向が変わったか

        if (dirChanged) {
            if (moveSmoothCounter <= 0) {
                finalKey.U = result.U;
                finalKey.D = result.D;
                finalKey.L = result.L;
                finalKey.R = result.R;
                moveSmoothCounter = MOVE_SMOOTH_FRAMES;//前フレームの入力を保持して滑らかに切り替える
            } else {
                finalKey.U = lastOutputKey.U;
                finalKey.D = lastOutputKey.D;
                finalKey.L = lastOutputKey.L;
                finalKey.R = lastOutputKey.R;
                moveSmoothCounter--;
            }
        } else {//スムージング期間中：急に切り替えず、前フレームの方向入力を維持する
            finalKey.U = result.U;
            finalKey.D = result.D;
            finalKey.L = result.L;
            finalKey.R = result.R;
            moveSmoothCounter = 0;
        }

        lastOutputKey = finalKey;
        return finalKey;
    }

    //Key の内容をコピーする（FightingICEのKeyは U, D, L, R, A, B, C） 
    private void copyKey(Key src, Key dst) {
         //攻撃ボタンのコピー（A/B/C）
        dst.A = src.A;
        dst.B = src.B;
        dst.C = src.C;
         //方向キーのコピー（U/D/L/R）
        dst.U = src.U;
        dst.D = src.D;
        dst.L = src.L;
        dst.R = src.R;
    }
}
