# HoloBar AI Assistant

## Project Overview

HoloBar is an Android application that integrates advanced text-to-speech and natural language processing capabilities to create an interactive AI assistant. The app allows users to communicate with AI agents through text or voice input and receive responses in both text and audio formats.

## Key Features

- Text and voice input support
- Multiple AI agent personalities (speakers)
- Text-to-speech synthesis using a custom VITS (Variational Inference with adversarial learning for end-to-end Text-to-Speech) model
- Natural language processing using the Gemini API
- Background music playback
- Conversation history management

## Main Components

### 1. MainActivity

The central activity of the application, responsible for:
- Initializing UI components
- Managing user interactions
- Handling audio playback (both TTS and background music)
- Coordinating between various components (speech recognition, text processing, etc.)

### 2. ProcessText

Manages the text processing pipeline:
- Interfaces with the Gemini API for natural language processing
- Coordinates with VitsOnnxSynthesizer for text-to-speech conversion

### 3. VitsOnnxSynthesizer

Handles text-to-speech synthesis:
- Uses an ONNX runtime to run a custom VITS model
- Converts text input into audio output

### 4. Tokenizer

Preprocesses text for the VITS model:
- Cleans and normalizes input text
- Expands abbreviations and converts numbers to words
- Tokenizes text into a format suitable for the VITS model

### 5. Phonemizer

Converts tokenized text into phonemes:
- Uses an ONNX model for grapheme-to-phoneme conversion
- Caches results for efficiency

### 6. ApiCall

Manages communication with the backend API:
- Sends user input to the server
- Retrieves AI-generated responses
- Handles conversation history

### 7. SpeechRecognitionManager

Manages speech-to-text functionality:
- Interfaces with Android's speech recognition capabilities
- Converts user's voice input into text

### 8. CustomVideoView

A custom implementation of Android's VideoView:
- Potentially used for displaying video content (implementation details not provided)

## Technical Details

- Language: Kotlin (with some Java components)
- AI Model: Custom VITS model for TTS, Gemini API for NLP
- ONNX Runtime: Used for running AI models on-device
- Concurrency: Utilizes Kotlin coroutines for asynchronous operations
- Caching: Implements caching mechanisms for improved performance

## Setup and Configuration

(Include instructions for setting up the project, required API keys, and any necessary configuration steps)

## Usage

(Provide basic instructions on how to use the app, including available commands or interactions)

## Dependencies

- ONNX Runtime
- Volley (for network requests)
- Apache Commons Lang
- ICU4J (for text processing)

## Future Improvements

(List any planned features or improvements)

## Contributing

(Instructions for contributors, if applicable)

## License

(Specify the license under which the project is released)