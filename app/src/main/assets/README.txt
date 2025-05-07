# TensorFlow Lite Model for Sketch2Real

A placeholder sketch2real.tflite file has been added to this directory. For production use, replace it with a properly trained TensorFlow Lite model.

## Model Requirements
- Architecture: Pix2Pix GAN or CycleGAN for sketch-to-photo translation
- Input size: 256x256 RGB image
- Output size: 256x256 RGB image
- Input normalization: [-1, 1]
- Output normalization: [-1, 1]

## How to Replace the Placeholder
1. Train a model using TensorFlow/PyTorch with a Pix2Pix or CycleGAN architecture
2. Convert the model to TensorFlow Lite format using TensorFlow's converter
3. Ensure the model accepts and outputs the correct dimensions and normalization
4. Replace the placeholder sketch2real.tflite file with your trained model

## Resources for Model Training
- TensorFlow Pix2Pix tutorial: https://www.tensorflow.org/tutorials/generative/pix2pix
- TensorFlow Lite conversion: https://www.tensorflow.org/lite/convert

Note: For development and testing purposes, the FaceConverter class includes a mockConversion method that simulates the conversion process when the actual model is not available or fails to load.
