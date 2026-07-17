# Set up
- keycloak clients
  - did:web:edc-proxy-connector1%3A80:connector1
    - secret EqdQ8TRr4AbROpFrXDr3a4r8pEzQQjN7
  - did:web:edc-proxy-connector2%3A80:connector2
    - secret 5UBhXDOzRvQpu3CiUPXNTn2tXxEAqlje
  - did:web:edc-proxy-connectorcp%3A80:connectorcp
    - secret: 6QU85OYXvSIu2gqs4p9c548nnUWxL5tM  
- network
  - internal ports
    - credentilas: 7091
    - identity: 7092
    - management: 8081
    - dsp: 8082
    - catalog: 8084
    - public: 10011
  - issuerservice
    - ports 7000:80
  - connector1
    - ports 9876:80
  - connector2
    - ports 9877:80
  - connectorcp
    - ports 9879:80

# datasets

Connector1