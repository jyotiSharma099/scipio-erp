# Use OpenJDK 8 as the base image
FROM adoptopenjdk/openjdk8:latest

# Set the working directory
WORKDIR /app

# Copy the entire repository to the working directory
COPY . /app

# Install Maven for building the project
RUN apt-get update && apt-get install -y maven

# Clean up apt-get cache
RUN apt-get clean && rm -rf /var/lib/apt/lists/*

# Compile the project using Maven
RUN mvn clean install

# Set environment variables
ENV LANG=C.UTF-8
ENV JAVA_HOME=/usr/lib/jvm/java-1.8-openjdk
ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/lib/jvm/java-1.8-openjdk/jre/bin:/usr/lib/jvm/java-1.8-openjdk/bin
ENV JAVA_VERSION=8u111
ENV JAVA_ALPINE_VERSION=8.111.14-r0
ENV SCIPIO_VERSION=1.14.4
ENV SCIPIO_TGZ_URL=https://github.com/ilscipio/scipio-erp/archive/v1.14.4.tar.gz
ENV SCIPIO_HOME=/opt/scipio
ENV PATH=/opt/scipio/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/lib/jvm/java-1.8-openjdk/jre/bin:/usr/lib/jvm/java-1.8-openjdk/bin

# Create the Scipio ERP directory
RUN mkdir -p "$SCIPIO_HOME"

# Switch to the Scipio ERP directory
WORKDIR /opt/scipio

# Download and extract Scipio ERP
RUN set -x && \
    curl -L "$SCIPIO_TGZ_URL" | tar xz --strip-components=1

# Expose the Tomcat server ports
EXPOSE 8080 8443 8983

# Set the default command to start Scipio ERP
CMD ["sh" "./start.sh"]
