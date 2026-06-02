package ai;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;

import aiinterface.CommandCenter;
import enumerate.Action;
import simulator.Simulator;
import struct.CharacterData;
import struct.FrameData;
import struct.GameData;
import struct.MotionData;

public class Node {

    private FrameData frameData;
    private Node parent;
    private LinkedList<Node> children;
    private LinkedList<Action> myActions;
    private LinkedList<Action> oppActions;
    private GameData gameData;
    private boolean playerNumber;
    private CommandCenter commandCenter;

    // ★ このノードが担当する自分の行動
    private Action myAction;

    // このノードが MCTS によって何回訪問されたか
    private int visit;
    // UCB1 の値（行動選択の指標）
    private double ucb;

    // 乱数生成器
    private Random rnd;
    // 未来予測インスタンス（この簡易版ではほぼ未使用）
    private Simulator simulator;

    private CharacterData myCharacter;
    private CharacterData oppCharacter;
    private ArrayList<MotionData> myMotion;
    private ArrayList<MotionData> oppMotion;

    // 木の深さ（0がルート）
    private int depth;

    // 何フレーム先までシミュレートするか（14F）
    private static final int FRAME_AHEAD = 14;
    // 探索の最大深さ（2手先を想定・この簡易版では未使用）
    private static final int MAX_DEPTH = 2;
    // UCB1 の定数 c
    private static final int UCB_CONST = 1;

    // ★ myAction を引数に追加したコンストラクタ
    public Node(FrameData frameData, Node parent,
                LinkedList<Action> myActions,
                LinkedList<Action> oppActions,
                GameData gameData,
                boolean playerNumber,
                CommandCenter commandCenter,
                Action myAction) {

        this.frameData = frameData;
        this.parent = parent;
        this.myActions = myActions;
        this.oppActions = oppActions;
        this.gameData = gameData;
        this.playerNumber = playerNumber;
        this.commandCenter = commandCenter;
        this.myAction = myAction;

        this.children = new LinkedList<>();

        this.visit = 0;
        this.ucb = 0;

        this.rnd = new Random();
        this.simulator = gameData.getSimulator();

        this.myMotion = gameData.getMotionData(playerNumber);
        this.oppMotion = gameData.getMotionData(!playerNumber);

        if (parent == null) {
            this.depth = 0;
        } else {
            this.depth = parent.depth + 1;
        }
    }

    // 子ノードの生成：自分の行動候補ごとに1ノードずつ作る
    public void createNode() {
        for (Action act : myActions) {
            children.add(new Node(
                    frameData,
                    this,
                    myActions,
                    oppActions,
                    gameData,
                    playerNumber,
                    commandCenter,
                    act // ★ このノードは act という行動を表す
            ));
        }

        // oppActions 側は今の設計では使っていないので一旦無視
        // （将来「相手行動も読む」設計にするなら別途拡張）
    }

    public Action mcts() {
        Node best = null;
        double bestUcb = -99999;

        for (Node nd : children) {
            nd.ucb = calcUcb(nd);
            if (nd.ucb > bestUcb) {
                bestUcb = nd.ucb;
                best = nd;
            }
        }

        // 子ノードがない場合の保険（基本的には起きない想定）
        if (best == null) {
            return myActions.get(rnd.nextInt(myActions.size()));
        }

        // ★ UCB が最大だったノードが担当する行動を返す
        return best.myAction;
    }

    private double calcUcb(Node nd) {
        if (nd.visit == 0) return 9999;
        // this.visit が 0 のときの log(0) 回避のため +1.0
        return (nd.getScore() / nd.visit)
                + UCB_CONST * Math.sqrt(Math.log(this.visit + 1.0) / nd.visit);
    }

    // ★ 評価関数：ここに感情バイアスを足す
    private double getScore() {
        int myHp  = frameData.getCharacter(playerNumber).getHp();
        int oppHp = frameData.getCharacter(!playerNumber).getHp();

        double baseScore = myHp - oppHp;  // HP差を基本スコアとする

        return baseScore + emotionBias(myAction);
    }

    // ★ 感情モデルと行動からバイアスを返す場所
    // 今はまだ未実装なので 0 を返しておく（あとで中身を書く）
    private double emotionBias(Action act) {
        // 例：
        // if (anger > 0.7 && isOffense(act)) return 20.0;
        // if (fear  > 0.7 && isRetreat(act)) return 15.0;
        return 0.0;
    }
}
