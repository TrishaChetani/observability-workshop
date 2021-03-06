#!/usr/bin/env bash
set -e

cd /tmp

echo "Waiting for imgeholder service to come up"

until $(curl --output /dev/null --silent --head --fail http://imageholder:8080/actuator/health); do
      printf '.'
      sleep 5
done

echo "imageholder running - uploading images"

/usr/local/bin/upload-traffic-gen -f false -u "http://imageholder:8080/api/images" -d /images | \
  /usr/local/bin/vegeta attack -rate=1/s -lazy -format=json -duration=1m | \
  vegeta report -type json


echo "Uploaded images"

echo "Waiting for imageorchestrator service to come up"

until $(curl --output /dev/null --silent --head --fail http://imageorchestrator:8080/actuator/health); do
      printf '.'
      sleep 5
done

echo "imageorchestrator running - sending random transformation requests"

/usr/local/bin/transform-traffic-gen  -n 30000 -f  "http://imageholder:8080/api/images/nameContaining/uploaded" -t  "http://imageorchestrator:8080/api/images/transform" | \
  /usr/local/bin/vegeta attack -rate=12/m -lazy -format=json > /dev/null
