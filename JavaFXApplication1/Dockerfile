FROM pedrombmachado/ntu_lubuntu:comp20081

# Install additional packages for graphics support
RUN apt-get update && apt-get install -y \
    appmenu-gtk3-module \
    libgtk-3-0 \
    mesa-utils \
    xorg \
    libgl1-mesa-dri \
    libgl1-mesa-glx \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /home/ntu-user/NetBeansProjects

# Keep container running
CMD ["tail", "-f", "/dev/null"]
