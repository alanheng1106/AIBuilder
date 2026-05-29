# AIBuilder 🏗️

A powerful, highly-configurable Minecraft Paper/Purpur plugin that leverages state-of-the-art AI models (Gemini, OpenAI, DeepSeek, and Ollama) to generate buildings and furniture directly in your world. Simply type a description of what you want, select a size, confirm the placement with a specialized selector stick, and watch the AI construct it block-by-block with fluid progress animations.

---

## Features

- **Multi-Provider AI support**: Seamless integration with **Google Gemini** (default), **OpenAI**, **DeepSeek**, and **Ollama** (for local/self-hosted or custom cloud models).
- **Interactive Building Sizes**: Choose between **Small**, **Medium**, or **Large** building scales. If you don't specify a size in the command, an interactive UI menu will guide you.
- **Smart Structure Generation**: Builds are not just empty shells; they include detailed interior designs and furniture.
- **Animated Batched Placement**: Blocks are placed sequentially with a configurable speed, complete with progress updates on your Action Bar, sound effects, and particle animations.
- **AI Build Placer Tool**: When generation completes, the plugin prompts you with a confirmation message and gives you a special placement stick. Right-click any block to place the structure exactly where you want.
- **Robust Quota System**: Limit block placements with a customizable daily quota per player. OP players bypass this quota automatically.
- **Transactional Undo**: Made a mistake or don't like the AI's build? Use `/aiundo` to instantly remove the structure and refund your daily block quota.
- **Adventure API Powered UI**: A polished, modern, and emoji-free chat panel interface.

---

## Commands & Permissions

| Command | Aliases | Description | Permission | Default |
| :--- | :--- | :--- | :--- | :--- |
| `/aibuild [size] <prompt>` | `/aib` | Request the AI to generate a structure. | `aibuilder.command.build` | OP |
| `/aiundo` | `/aiu` | Undo your last AI building, restoring blocks and quota. | `aibuilder.command.undo` | OP |
| `/aiquota` | `/aiq` | Check your remaining daily block quota. | `aibuilder.command.quota` | Everyone |
| `/aireload` | | Reload the plugin configuration file (`config.yml`). | `aibuilder.command.reload` | OP |

---

## Installation

### Requirements
- **Minecraft Server**: Paper, Purpur, or any other compatible fork targeting version **1.20+** (specifically tested on `26.1.2`).
- **Java**: Java **26** or newer.

### Quick Start (Precompiled Release)
1. Go to the [Releases](https://github.com/alanheng1106/AIBuilder/releases) page.
2. Download the latest `AIBuilder-1.0.0.jar` release package.
3. Drop the JAR file into your Minecraft server's `plugins/` directory.
4. Start (or restart) your Minecraft server.

### Build from Source
If you prefer to compile the plugin yourself:
1. Clone this repository to your build machine.
2. Compile and package the plugin using Maven:
   ```bash
   mvn clean package
   ```
3. Copy the compiled JAR file from the `target/` directory:
   ```text
   target/AIBuilder-1.0.0.jar
   ```
   into your Minecraft server's `plugins/` directory.
4. Start (or restart) your Minecraft server.

---

## Configuration (`config.yml`)

The plugin can be fully customized through its `config.yml` file. Below is a breakdown of the available settings:

```yaml
# AI Builder Configuration

# The AI provider to use. Supported: "gemini", "openai", "ollama"
provider: "gemini"

# API Key for the provider. If empty, the plugin checks environment variables.
# Gemini:  GEMINI_API_KEY
# OpenAI:  OPENAI_API_KEY
# Ollama:  OLLAMA_API_KEY (optional — leave blank for local Ollama without auth)
api-key: ""

# The model name to use.
# For Gemini: gemini-3.5-flash, gemini-3.1-pro, gemini-3.1-flash-lite, etc.
# For OpenAI: gpt-5.5, gpt-5.4, gpt-5.4-mini, etc.
# For Ollama: llama3, mistral, codellama, etc.
model: "gemini-3.5-flash"

# Custom API endpoint URL. Leave blank to use the default for the chosen provider.
#   Gemini:  https://generativelanguage.googleapis.com
#   OpenAI:  https://api.openai.com
#   Ollama:  http://localhost:11434 (override to point at custom cloud hosts)
api-url: ""

# Building speed settings:
# delay-between-batches: Number of ticks (1 tick = 50ms) between block placements.
# Set to 0 to place all blocks instantly.
delay-between-batches: 1

# Number of blocks to place in each batch.
# For smoother animations, use small numbers (e.g., 2-5).
# For faster builds, increase this number.
blocks-per-batch: 5

# Predefined scales configuration profiles for /aibuild [scale] <prompt>
scales:
  small:
    max-dimension: 15
    max-blocks: 1000
  medium:
    max-dimension: 30
    max-blocks: 3000
  large:
    max-dimension: 50
    max-blocks: 8000

# Whether to play sound and particle effects when placing blocks.
effects-enabled: true

# Daily block placement quota per player. Set to -1 for unlimited.
daily-block-quota: 10000
```

---

## Provider Setup

### 1. Google Gemini (Default)
Set the `provider` in `config.yml` to `"gemini"`. You can either:
- Paste your API key in the `api-key` field of `config.yml`.
- Or set the `GEMINI_API_KEY` environment variable in your server's startup script.

Recommended models: `gemini-3.5-flash` (fast & cheap), `gemini-3.1-pro` or `gemini-3.1-flash-lite`.

### 2. OpenAI
Set the `provider` in `config.yml` to `"openai"`. You can either:
- Paste your API key in the `api-key` field of `config.yml`.
- Or set the `OPENAI_API_KEY` environment variable.

Recommended models: `gpt-5.4-mini` (fast and highly efficient), `gpt-5.4`, or `gpt-5.5`.

### 3. DeepSeek
Set the `provider` in `config.yml` to `"deepseek"`. You can either:
- Paste your API key in the `api-key` field of `config.yml`.
- Or set the `DEEPSEEK_API_KEY` environment variable.

Recommended models: `deepseek-chat`.
Default endpoint: `https://api.deepseek.com`

### 4. Ollama
For local model execution or hosting Ollama on another server.
- Set `provider` to `"ollama"`.
- Set `api-url` to your Ollama endpoint (e.g. `http://localhost:11434` or `http://192.168.1.100:11434`).
- Specify your model (e.g., `llama3`, `mistral`, `codellama`, etc.). Make sure you run `ollama run <model>` first on the host system to download it.

---

## How It Works Under the Hood

1. **AI Querying**: When you run `/aibuild <prompt>`, the plugin structures a system prompt forcing the AI to output the design as a JSON array of cuboids. Each cuboid defines a block type and its start/end coordinates, enabling efficient multi-block placement (including structure shells and interior furniture details).
2. **Interactive Selection Stick**: Once the AI generation is successful, a confirmation message is sent, and you are given the **AI Build Placer** tool.
3. **Placing & Rendering**: Right-clicking a block with the Placer stick starts a scheduler. It translates the cuboids relative to the clicked block and places them in batches (customizable via `blocks-per-batch` and `delay-between-batches`) to avoid lag and provide a beautiful construction animation.
4. **Quota Verification**: The plugin deducts the placed block count from the player's daily limit (saved in `quota.yml` under your plugin data folder). If the player runs out of quota, they cannot build further unless they are OP or wait for the daily reset.
5. **Transactional Undo**: The coordinates of the blocks placed are stored per player. `/aiundo` restores the original blocks (reverting the landscape) and refunds the player's quota.

---

## License

This project is licensed under the MIT License - see the [LICENSE](file:///mnt/sda1/AIBuilder/LICENSE) file for details.

