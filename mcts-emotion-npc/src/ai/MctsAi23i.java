package ai;

import java.util.ArrayList;
import java.util.LinkedList;

import aiinterface.AIInterface;        // FightingICE のAIとして認識
import aiinterface.CommandCenter;      // コマンド（技）入力の補助
import enumerate.Action;               // AIが選べる行動一覧
import enumerate.State;                // キャラの状態（AIRなど）
import simulator.Simulator;            // 未来予測（14F先など）を計算
import struct.CharacterData;           // キャラのHP/位置/行動など
import struct.FrameData;               // フレームごとの試合状況
import struct.GameData;                // ゲーム全体の情報
import struct.Key;                     // 入力（←→ABなど）を保持
import struct.MotionData;              // 技の発生F/硬直F/消費エネルギーなど

/**
 * MCTS(モンテカルロ木探索)で実装されたAI
 * 元作者: Taichi
 */
public class MctsAi23i implements AIInterface {

    // =========================
    // 基本フィールド
    // =========================
    private Simulator simulator;
    private Key key;
    private CommandCenter commandCenter;
    private boolean playerNumber;
    private GameData gameData;

    /** 大本のFrameData（現在フレームの情報） */
    private FrameData frameData;

    /** 大本よりFRAME_AHEAD分進めたFrameData（未来予測後の情報） */
    private FrameData simulatorAheadFrameData;

    /** 自分が行える行動の一覧 */
    private LinkedList<Action> myActions;

    /** 相手が行える行動の一覧 */
    private LinkedList<Action> oppActions;

    /** 自分と相手の情報 */
    private CharacterData myCharacter;
    private CharacterData oppCharacter;

    /** 先読みフレーム数 */
    private static final int FRAME_AHEAD = 14;

    private ArrayList<MotionData> myMotion;
    private ArrayList<MotionData> oppMotion;

    private Action[] actionAir;
    private Action[] actionGround;
    private Action spSkill;

    private Node rootNode;

    /** デバッグモード（trueで木の情報を出力） */
    public static final boolean DEBUG_MODE = false;

    // =========================
    // ライフサイクル
    // =========================

    /** 大戦終了時に呼ばれるメソッド */
    @Override
    public void close() {
        // 何もしない
    }

    /** AI初期化（1P/2P どちらに立つかを受け取る） */
    @Override
    public int initialize(GameData gameData, boolean playerNumber) {
        this.playerNumber = playerNumber;
        this.gameData = gameData;

        this.key = new Key();                 // 最終的にゲームへ送るキー入力
        this.frameData = new FrameData();     // 現在の試合状況
        this.commandCenter = new CommandCenter(); // 技入力補助

        this.myActions = new LinkedList<>();  // 自分の行動候補
        this.oppActions = new LinkedList<>(); // 相手の行動候補

        this.simulator = gameData.getSimulator(); // 未来予測エンジン

        // 空中行動の候補
        actionAir = new Action[] {
            Action.AIR_GUARD,           // 空中ガード
            Action.AIR_A, Action.AIR_B, // 空中攻撃
            Action.AIR_DA, Action.AIR_DB, // 空中下攻撃
            Action.AIR_FA, Action.AIR_FB, // 空中前攻撃
            Action.AIR_UA, Action.AIR_UB, // 空中上攻撃
            Action.AIR_D_DF_FA, Action.AIR_D_DF_FB, // 空中236（波動系）
            Action.AIR_F_D_DFA, Action.AIR_F_D_DFB, // 空中623（昇龍系）
            Action.AIR_D_DB_BA, Action.AIR_D_DB_BB  // 空中214（竜巻系）
        };

        // 地上行動の候補
        actionGround = new Action[] {
            Action.STAND_D_DB_BA,
            Action.BACK_STEP,
            Action.FORWARD_WALK,
            Action.DASH,
            Action.JUMP,
            Action.FOR_JUMP,
            Action.BACK_JUMP,
            Action.STAND_GUARD,
            Action.CROUCH_GUARD,
            Action.THROW_A,
            Action.THROW_B,
            Action.STAND_A,
            Action.STAND_B,
            Action.CROUCH_A,
            Action.CROUCH_B,
            Action.STAND_FA,
            Action.STAND_FB,
            Action.CROUCH_FA,
            Action.CROUCH_FB,
            Action.STAND_D_DF_FA,
            Action.STAND_D_DF_FB,
            Action.STAND_F_D_DFA,
            Action.STAND_F_D_DFB,
            Action.STAND_D_DB_BB
        };

        // 必殺技（特殊）
        spSkill = Action.STAND_D_DF_FC;

        // モーションデータ取得（未来予測で使用）
        myMotion  = gameData.getMotionData(this.playerNumber);
        oppMotion = gameData.getMotionData(!this.playerNumber);

        return 0; // 初期化成功
    }

    // =========================
    // 入力・処理ループ
    // =========================

    /** AIが今どのキーを入力しているかの問い合わせ */
    @Override
    public Key input() {
        return key;
    }

    /** 毎フレーム呼ばれる行動決定 */
    @Override
    public void processing() {
        // FrameDataがまだ無い/ラウンド終了などの場合は処理しない
        if (!canProcessing()) {
            return;
        }

        // 技を出している途中なら、そのまま継続
        if (commandCenter.getSkillFlag()) {
            key = commandCenter.getSkillKey();
            return;
        }

        // 新しく行動を決める
        key.empty();
        commandCenter.skillCancel();

        // MCTS 用の下準備（先読みなど）
        mctsPrepare();

        // ルートノード生成（MCTSの最重要部分）
        rootNode = new Node(
            simulatorAheadFrameData, // 14フレーム先のデータ
            null,                    // 親ノードなし
            myActions,               // 自分の行動候補
            oppActions,              // 相手の行動候補
            gameData,                // ゲーム情報
            playerNumber,            // 1P/2P
            commandCenter,           // コマンド補助
            null                     // ルートは行動なし
        );

        // 探索木構築
        rootNode.createNode();

        // MCTS実行して最善手を取得
        Action bestAction = rootNode.mcts();

        // 選ばれた行動をコマンドとして送る
        commandCenter.commandCall(bestAction.name());
    }

    /** AIが行動できる状態かどうか */
    public boolean canProcessing() {
        return !frameData.getEmptyFlag()
            && frameData.getRemainingFramesNumber() > 0;
    }

    // =========================
    // MCTSの下準備
    // =========================

    /**
     * MCTSの前準備（未来予測 + 行動リスト作成）
     * 先読みシミュレーションや行動候補リストの更新
     */
    public void mctsPrepare() {
        simulatorAheadFrameData = simulator.simulate(
            frameData,
            playerNumber,
            null,
            null,
            FRAME_AHEAD
        );

        myCharacter  = simulatorAheadFrameData.getCharacter(playerNumber);
        oppCharacter = simulatorAheadFrameData.getCharacter(!playerNumber);

        setMyAction();
        setOppAction();
    }

    /** 自分の行動候補を energy に応じて作る */
    public void setMyAction() {
        myActions.clear();
        int energy = myCharacter.getEnergy();

        if (myCharacter.getState() == State.AIR) {
            for (Action act : actionAir) {
                if (Math.abs(myMotion.get(act.ordinal()).getAttackStartAddEnergy()) <= energy) {
                    myActions.add(act);
                }
            }
        } else {
            // 必殺技
            if (Math.abs(myMotion.get(spSkill.ordinal()).getAttackStartAddEnergy()) <= energy) {
                myActions.add(spSkill);
            }
            // 地上技
            for (Action act : actionGround) {
                if (Math.abs(myMotion.get(act.ordinal()).getAttackStartAddEnergy()) <= energy) {
                    myActions.add(act);
                }
            }
        }
    }

    /** 相手の行動候補を energy に応じて作る */
    public void setOppAction() {
        oppActions.clear();
        int energy = oppCharacter.getEnergy();

        if (oppCharacter.getState() == State.AIR) {
            for (Action act : actionAir) {
                if (Math.abs(oppMotion.get(act.ordinal()).getAttackStartAddEnergy()) <= energy) {
                    oppActions.add(act);
                }
            }
        } else {
            // 必殺技
            if (Math.abs(oppMotion.get(spSkill.ordinal()).getAttackStartAddEnergy()) <= energy) {
                oppActions.add(spSkill);
            }
            // 地上技
            for (Action act : actionGround) {
                if (Math.abs(oppMotion.get(act.ordinal()).getAttackStartAddEnergy()) <= energy) {
                    oppActions.add(act);
                }
            }
        }
    }

    // =========================
    // FightingICEイベント
    // =========================

    @Override
    public void roundEnd(int p1Hp, int p2Hp, int frames) {
        // ここでは特にリセット処理はしていない
    }

    /** 毎フレーム呼ばれる：FrameDataを受け取る */
    @Override
    public void getInformation(FrameData frameData, boolean isControl) {
        this.frameData = frameData;
        this.commandCenter.setFrameData(this.frameData, playerNumber);

        myCharacter  = frameData.getCharacter(playerNumber);
        oppCharacter = frameData.getCharacter(!playerNumber);
    }

    public void getInformation(FrameData frameData) {
        // 未使用
    }
}
