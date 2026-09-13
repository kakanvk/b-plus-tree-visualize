# B+ Tree Visualizer

A production-style JavaFX desktop application for learning and presenting B+ Tree operations. The application separates the verified data structure from operation tracing, layout, rendering, and playback so every algorithm step can be explained independently.

## Requirements

- Java 21
- Maven 3.9+

JavaFX 21 and AtlantaFX 2.1.0 are resolved from Maven Central. A separate JavaFX SDK or manual module-path configuration is not required. The interface uses AtlantaFX Primer Light as its base theme and project-specific CSS for its visual identity.

## Run

```bash
mvn clean javafx:run
```

## Build and test

```bash
mvn clean test
mvn clean package
```

On Windows, `mvn clean package` also invokes Java 21 `jpackage` and creates a self-contained portable application at:

```text
target/dist/BPlusTreeVisualizer/BPlusTreeVisualizer.exe
```

Run the executable directly or distribute the entire `BPlusTreeVisualizer` directory. Do not copy the `.exe` alone because it requires the adjacent `app` and `runtime` directories. A ready-to-share build is also available at:

```text
release/BPlusTreeVisualizer/BPlusTreeVisualizer.exe
release/BPlusTreeVisualizer-1.0.0-windows.zip
```

## Features

- Insert and reject duplicate integer keys
- Search with comparison and path steps
- Delete with redistribution/borrow, merge, separator repair, and root shrink
- Inclusive range search through the linked leaf sequence
- Configurable B+ Tree order from 3 to 8
- Automatic non-overlapping subtree layout
- Distinct internal nodes, leaf nodes, parent edges, and linked-leaf edges
- Direct algorithm checkpoints for traversal, insert, split, borrow, merge, and root changes
- Immutable per-step tree snapshots with stable node IDs and exact edge/key highlighting
- Deterministic play, pause, previous, next, restart, jump, and speed controls
- Inline insertion input with full signed integer validation
- 13 curated datasets for splits, merges, ranges, negative and long keys
- Random insertion by quantity, with an advanced custom numeric range
- Inline validation feedback, empty state, statistics, and comparison count
- Full invariant validation in the algorithm layer

## Project structure

```text
src/main/java/com/bplustree/visualizer/
├── model/          B+ Tree nodes, mutations, invariants, and statistics
├── service/        Operations and educational event generation
├── event/          UI-independent animation event contract
├── visualization/ Layout engine, JavaFX nodes/edges, renderer, playback
├── controller/     Dialog input, validation, responsive UI coordination
└── ui/             JavaFX application launchers
```

The tokenized stylesheet is stored separately at `src/main/resources/styles/app.css`. Algorithm tests live under `src/test/java` and intentionally do not depend on JavaFX.

Animation steps are captured synchronously inside the B+ Tree algorithm, following the checkpoint boundaries demonstrated by `ref/BPlusTree.js`. Each event stores an immutable structural snapshot, its exact node/key/edge target, and the cumulative comparison count. Playback therefore seeks directly to a snapshot and never replays or reverses mutations against the canonical tree.

## B+ Tree overview

A B+ Tree is a balanced multi-way search tree designed to keep its height small.

- **Internal nodes** contain separator keys and child pointers. In this implementation, a separator equals the smallest key in its right child subtree.
- **Leaf nodes** contain every stored key in sorted order. Leaves are connected in both directions, making sequential and range access efficient.
- **Order** is the maximum number of children in an internal node. A leaf stores at most `order - 1` keys.
- **Split** occurs after overflow. A full node is divided and a separator is copied into its parent; splitting can cascade to a new root.
- **Redistribution** borrows a key or child from a sibling when deletion causes underflow and that sibling has spare occupancy.
- **Merge** combines siblings when neither can lend. This can cascade upward and may shrink the root.

Search, insert, and delete take `O(log n)` tree navigation time. A range query takes `O(log n + k)`, where `k` is the number of returned keys.

## Demonstration tips

1. Choose **Split demo**, then insert additional nearby keys to show leaf and internal splits.
2. Choose **Merge demo**, then delete `30` to demonstrate underflow repair.
3. Use **Pause** and the previous/next controls while presenting each generated algorithm step.
4. Drag the visualization surface or use its scrollbars when a large tree exceeds the viewport.
