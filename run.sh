
BROKER_COUNT="${BROKER_COUNT:-3}"
BROKER_PREFIX="${BROKER_PREFIX:-server}"
IMAGE_NAME="${IMAGE_NAME:-ubuntu-kafka}"
NETWORK_NAME="${NETWORK_NAME:-my-kafka-cluster-net}"
OUTSIDE_DOMAIN_PREFIX="${OUTSIDE_DOMAIN_PREFIX:-mydomain.com}"
INTERNAL_PORT="${INTERNAL_PORT:-9092}"
EXTERNAL_PORT="${EXTERNAL_PORT:-8092}"
LOCALHOST_PORT="${LOCALHOST_PORT:-7092}"

IMAGE_NAME=ubuntu-kafka

networkExists=`docker network ls | grep $NETWORK_NAME`
if [[ ! -z "$networkExists" ]]
then
  echo "Network $NETWORK_NAME already exists"
else
  echo "Creating Network $NETWORK_NAME"
  docker network create $NETWORK_NAME
fi

for ((i = 0; i < BROKER_COUNT; i++)); do
  name="$BROKER_PREFIX-$i"
  serverId=`docker ps -q -f "name=$name"`
  if [[ ! -z $serverId ]]
  then
    echo "Stopping $name"
    docker kill "$name"
  fi
done

sleep 2

for ((i = 0; i < BROKER_COUNT; i++)); do
  name="$BROKER_PREFIX-$i"
  echo "Starting $name"
  localhostport=$((LOCALHOST_PORT + i))
  localhostportForward="-p$localhostport:$localhostport"

  docker run -d --rm --name "$name" \
    -e "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=$BROKER_COUNT" \
    -e "BROKER_PREFIX=$BROKER_PREFIX" -e "BROKER_ID=$i" \
    -e "BROKER_COUNT=$BROKER_COUNT" \
    -e "OUTSIDE_DOMAIN_PREFIX=$OUTSIDE_DOMAIN_PREFIX" \
    -e "INTERNAL_PORT=$INTERNAL_PORT" \
    -e "EXTERNAL_PORT=$EXTERNAL_PORT" \
    -e "LOCALHOST_PORT=$LOCALHOST_PORT" \
    $localhostportForward --network $NETWORK_NAME -t $IMAGE_NAME
done

