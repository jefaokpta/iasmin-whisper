FROM python:3-slim

# Install system dependencies including FFmpeg
RUN apt-get update && apt-get install -y \
    ffmpeg \
    git \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Upgrade pip and install Whisper
RUN pip3 install --no-cache-dir --upgrade pip && \
    pip3 install --no-cache-dir git+https://github.com/openai/whisper.git

# Create directory for audio files
RUN mkdir /data
RUN mkdir /data/audios
RUN mkdir /data/transcripts

# Set the working directory to the audio directory
WORKDIR /data

# Command to keep container running (will be overridden by docker-compose command)
CMD ["bash"]