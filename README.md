# Sketch2Real: AI-Powered Sketch to Real Face Converter

An Android application that converts sketch faces to realistic human faces with natural coloring using AI.

## Features

- Capture sketches using the device camera
- Import sketches from the gallery
- Convert sketches to realistic human faces with natural skin tones and colors
- Save converted images to the gallery
- Share converted images with other apps

## Technical Implementation

### Architecture

The application follows a simple architecture with the following components:

- **MainActivity**: Handles the UI interactions and orchestrates the conversion process
- **FaceConverter**: Manages the TensorFlow Lite model and performs the sketch-to-real conversion

### AI Model

The application uses a TensorFlow Lite model based on Pix2Pix GAN architecture, which is specifically trained to convert sketch faces to realistic human faces. The model is optimized for mobile devices and performs inference directly on the device without requiring an internet connection.

### Requirements

- Android 5.0 (API level 21) or higher
- Camera permission for capturing sketches
- Storage permission for saving images (on Android 9 and below)

## Development Setup

1. Clone the repository
2. Open the project in Android Studio
3. Download the TensorFlow Lite model file (`sketch2real.tflite`) and place it in the `app/src/main/assets` directory
4. Build and run the application

## Model Training

The sketch-to-real face conversion model was trained using a dataset of paired sketch and real face images. The training process involved:

1. Data collection and preprocessing
2. Training a Pix2Pix GAN model
3. Optimizing and converting the model to TensorFlow Lite format

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- TensorFlow team for providing the TensorFlow Lite framework
- The original Pix2Pix paper authors for the GAN architecture