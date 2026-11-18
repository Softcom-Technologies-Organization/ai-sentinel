# Trio model releases

| Model | Revision | Published license |
|---|---|---|
| [Bards EU PII v3](https://huggingface.co/bardsai/eu-pii-anonimization-multilang-v3/tree/091984b5fbce4c161abc4776ed0f4e3206e1b374) | `091984b5fbce4c161abc4776ed0f4e3206e1b374` | Apache-2.0 |
| [CBX Secrets 32M](https://huggingface.co/CobuildX-AI/cbx-secrets-detector-32M/tree/d6af1fabe7e6404edd4545ad1d3f62b1e7eda475) | `d6af1fabe7e6404edd4545ad1d3f62b1e7eda475` | Apache-2.0 |
| [Ettin 68M Nemotron PII](https://huggingface.co/kalyan-ks/ettin-68m-nemotron-pii/tree/500262a2aaf913825ef750ef255c3fe437cd8e64) | `500262a2aaf913825ef750ef255c3fe437cd8e64` | MIT |

The Bards and CBX official decoders are loaded from their pinned snapshots only after checking
the SHA-256 hashes of the reviewed source. No unpinned remote module is executed.
Ettin's weights are Copyright 2026 Kalyan KS; see [MIT license](LICENSE-ETTIN.txt). Its decoder is
ours: the model-card formatter groups sub-word tokens label by label, which split one value into
fragments carrying different types.

Bards uses 512-token overflow windows with 128-token overlap. CBX retains its published
7,999-token context, 64-token overlap, BIO decoder, punctuation cleanup and minimum lengths.
Ettin uses 1,022-token windows with 128-token overlap, each token read from the window where it
sits farthest from a cut, then one label per whole word.
All three receive the complete original text and return Python character offsets.

Scores preserve the decoders' spans: Bards averages pooled BIO probabilities over contributing
token positions; regex email matches score 1. CBX averages winning-tag probabilities of the
retained tokens overlapping a span. Ettin scores a word by the mean probability of its label over
all the word's characters, so a word only partly labelled scores low.
These are model confidence estimates, not calibrated correctness probabilities. The initial
threshold of each type is the one measured in `benchmarks/trio-calibration` (annotated corpora
plus adjudicated findings on technical documentation); a type never right there starts disabled.
