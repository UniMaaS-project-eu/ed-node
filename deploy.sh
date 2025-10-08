#!/usr/bin/env bash
#
# This script manages a Docker Compose instance.
# It takes the instance name and an optional mode for the 'down' command.
#
# Usage: $0 <instance_name> [mode]
#
# Available modes:
#   recreate (default): Only builds and brings up containers, skipping the 'down' command.
#   fromzero: Executes 'docker-compose down -v', 'docker-compose build' and 'docker-compose up -d'.
#   removeall: Executes 'docker-compose down -v'.


set -e

# Read command-line parameters
INSTANCE_NAME=$1
MODE=$2

# Show help if requested
if [[ "$INSTANCE_NAME" == "-h" || "$INSTANCE_NAME" == "--help" ]]; then
  echo "Usage: $0 <instance_name> [mode]"
  echo
  echo "Manages a Docker Compose instance by giving it a unique project name."
  echo "The script performs the following actions:"
  echo "1. Exports variables from the '.env-<instance_name>' file."
  echo "2. Generates a 'docker-compose-<instance_name>.yml' file from a template."
  echo "3. Generates a 'nginx--<instance_name>.conf' file from a template."
  echo "4. Executes 'docker-compose down' command based on the specified mode, affecting only the specified project."
  echo "5. Builds the images for the specified project."
  echo "6. Lifts the containers in 'detached' mode for the specified project."
  echo
  echo "Parameters:"
  echo "  <instance_name>  The name of the instance to manage. This will be used as the Docker project name."
  echo "                   Required."
  echo "  [mode]           The mode for the 'down' command. Optional."
  echo "                   If not specified, 'recreate' mode is used."
  echo
  echo "Modes for the 'down' command:"
  echo "  recreate (or blank)   Skips the 'down' command completely, only performing 'build' and 'up'."
  echo "  fromzero              Stops services and removes containers and volumes for the project."
  echo "                        Equivalent to 'docker-compose down -v', finally performing 'build' and 'up'."
  echo "  removeall             Stops services and removes containers and volumes for the project."
  echo "                        Equivalent to 'docker-compose down -v'."
  echo
  echo "Examples:"
  echo "  $0 my-app"
  echo "  $0 my-app recreate"
  echo "  $0 my-app fromzero"
  echo "  $0 my-app removeall"
  exit 0
fi

# Validate that the instance name was provided
if [ -z "$INSTANCE_NAME" ]; then
  echo "Error: The instance name is required."
  echo "Usage: $0 <instance_name> [mode]"
  echo "For more information, use: $0 -h"
  exit 1
fi

# Instance-specific .env file
ENV_FILE=".env-${INSTANCE_NAME}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Error: The file $ENV_FILE does not exist."
  exit 1
fi

# Export variables from the .env file
set -o allexport
source "$ENV_FILE"
set +o allexport

# Generate instance-specific docker-compose.yml from the template
echo "Generating docker-compose-${INSTANCE_NAME}.yml..."
envsubst < docker-compose-template.yml > docker-compose-${INSTANCE_NAME}.yml

# Generate instance-specific nginx.conf from the template
echo "Generating nginx-${INSTANCE_NAME}.conf..."
#envsubst < deployment/nginx/nginx-template.conf > deployment/nginx/nginx-${INSTANCE_NAME}.conf
envsubst '$ED_NODE_INSTANCE_NAME' < deployment/nginx/nginx-template.conf > deployment/nginx/nginx-${INSTANCE_NAME}.conf

# Determine which 'down' command to run based on the mode
case "$MODE" in
  recreate|"")
    echo "Skipping the 'down' step..."
    ;;
  fromzero)
    echo "Executing 'down -v' (fromzero mode)..."
    #docker-compose --project-name "$INSTANCE_NAME" --env-file "$ENV_FILE" -f docker-compose-${INSTANCE_NAME}.yml down -v
    docker compose --project-name "$INSTANCE_NAME" --env-file "$ENV_FILE" -f docker-compose-${INSTANCE_NAME}.yml down -v
    ;;
  removeall)
    echo "Executing 'down -v' (removeall mode)..."
    #docker-compose --project-name "$INSTANCE_NAME" --env-file "$ENV_FILE" -f docker-compose-${INSTANCE_NAME}.yml down -v
    docker compose --project-name "$INSTANCE_NAME" --env-file "$ENV_FILE" -f docker-compose-${INSTANCE_NAME}.yml down -v
    echo "Process completed for instance '$INSTANCE_NAME'!"
    exit 0
    ;;
  *)
    echo "Invalid mode: $MODE"
    echo "Available modes: recreate, fromzero, removeall"
    echo "For more information, use: $0 -h"
    exit 1
    ;;
esac

# Build services
echo "Building images..."
#docker-compose --project-name "$INSTANCE_NAME" --env-file "$ENV_FILE" -f docker-compose-${INSTANCE_NAME}.yml build
docker compose --project-name "$INSTANCE_NAME" --env-file "$ENV_FILE" -f docker-compose-${INSTANCE_NAME}.yml build

# Lift services
echo "Lifting services..."
#docker-compose --project-name "$INSTANCE_NAME" --env-file "$ENV_FILE" -f docker-compose-${INSTANCE_NAME}.yml up -d
docker compose --project-name "$INSTANCE_NAME" --env-file "$ENV_FILE" -f docker-compose-${INSTANCE_NAME}.yml up -d

echo "Process completed for instance '$INSTANCE_NAME'!"
