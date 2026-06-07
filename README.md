モンテカルロ木探索と感情モデルを用いた2D格闘ゲームNPCの研究成果物
MCTS-EmotionNPC
概要

MCTS-EmotionNPCは、2D格闘ゲームAI「MctsAi23i」に感情モデルを統合したNPCである。

従来の格闘ゲームAIは勝率の最大化を目的として行動を選択するが、人間プレイヤーのような感情変化による戦術の揺らぎは考慮されていない。

本研究では、Plutchikの感情モデルとRussellのValence-Arousalモデルを組み合わせ、対戦中に発生する感情変化をNPCの行動選択へ反映することで、人間らしい戦闘行動を実現することを目的とした。

研究背景

対戦型格闘ゲームにおけるNPCは、高い勝率を実現できる一方で、

行動が機械的
戦術変化が乏しい
人間らしさに欠ける

という課題が存在する。

南・池田（2022）は感情を考慮したゲームAIの有効性を示しているが、学習ベースの手法が中心であった。

本研究ではルールベースによる感情モデルを採用し、設計者が意図した感情変化を直接行動へ反映できるNPCを提案する。

システム構成
Game State
     │
     ▼
状況認識
     │
     ▼
感情値更新
(Joy / Anger / Fear / Sadness)
     │
     ▼
感情強度判定
     │
     ▼
Valence-Arousal変換
     │
     ▼
戦術パラメータ更新
     │
     ▼
MCTS行動補正
     │
     ▼
最終行動出力
使用技術
Java
FightingICE
DareFightingICE 7.0
Monte Carlo Tree Search (MCTS)
Plutchik Emotion Model
Russell Circumplex Model
Object-Oriented Design
実装した機能
感情モデル

NPCは以下の4感情を保持する。

Emotion	説明
Joy	喜び
Anger	怒り
Fear	恐れ
Sadness	悲しみ

各感情は0～100で管理される。

感情誘発イベント

感情値は以下のイベントによって変化する。

与ダメージ
被ダメージ
HP差
コンボ成功
連続被弾
画面端拘束
感情強度
値
0～49 : 弱
50～89 : 中
90～100 : 強
Valence-Arousal変換

感情を行動判断へ利用するため2軸へ変換する。

Valence = Joy - Sadness
Arousal = Anger - Fear
Valence：快－不快
Arousal：覚醒－非覚醒
戦術パラメータ

感情値に応じて以下のパラメータを動的に変更する。

Attack
Defense
Evasion
Approach
Distance Control
Anti-Air Awareness
行動補正

感情状態によってMCTSの出力を補正する。

例：

Anger
攻撃頻度増加
接近行動増加
防御頻度減少
Fear
防御増加
回避増加
接近頻度低下
Sadness
消極的行動
距離維持増加
Joy
前進行動増加
攻撃継続率増加
行動自然化機構
ジャンプ攻撃抑制

MctsAi23iはAIR_FAを過度に選択する傾向がある。

そのため、

ジャンプ行動ペナルティ
評価値減算

を導入し、地上戦中心の行動へ調整した。

入力スムージング

人間らしい挙動を再現するため、

方向入力に3フレーム慣性

を導入した。

これにより急激な方向転換を抑制している。

ディレクトリ構成
src/
 ├─ ai/
 │   ├─ MctsAi23i.java
 │   ├─ MctsAi23iEmotion.java
 │
 ├─ emotion/
 │   ├─ EmotionModel
 │   ├─ EmotionRule
 │
 └─ util/
     └─ CsvLogger.java
技術的課題と解決
課題

既存のMctsAi23iを単純継承して感情処理を追加すると、
FightingICE内部の処理順序との依存関係により正常動作しなかった。

解決

MctsAi23iをベースに、

感情更新層
戦術パラメータ層
行動補正層

を段階的に追加する構造へ変更した。

これにより既存MCTSアルゴリズムを保持したまま感情機能を統合できた。

主な担当箇所
感情モデル設計
感情誘発条件設計
Valence-Arousal変換
MCTS行動補正機構
FightingICE環境構築
AI実装(Java)
実験ログ収集
評価実験
今後の課題
強化学習による感情獲得
Personality Modelの導入
LLMとの連携
マルチエージェント環境への拡張
感情推定の学習ベース化
研究成果

本研究では、感情状態に応じて攻撃・防御・回避行動が変化するNPCを実装した。

従来のMCTSベースNPCに対して、

感情に応じた戦術変化
行動の多様性向上
を実現した。
