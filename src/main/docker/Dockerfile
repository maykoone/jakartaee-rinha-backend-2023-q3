FROM payara/micro:6.2023.8-jdk17

ENV JVM_ARGS="-XX:InitialRAMPercentage=70.0"

COPY target/jakartaee-hello-world.war $DEPLOY_DIR/app.war
COPY payara-postbootcommand.txt /opt/payara
# ENTRYPOINT ["java", "-jar", "/opt/payara/payara-micro.jar", "--deploymentDir", "/opt/payara/deployments", "--contextroot", "/", "--nocluster", "--disablephonehome" ]
# CMD ["--deploymentDir", "/opt/payara/deployments", "--contextroot", "/", "--nocluster", "--disablephonehome", "--maxhttpthreads", "10", "--postbootcommandfile", "/opt/payara/payara-postbootcommand.txt" ]
# CMD ["--deploy", "/opt/payara/deployments/app.war", "--contextroot", "ROOT", "--nohazelcast", "--disablephonehome", "--maxhttpthreads", "5", "--minhttpthreads", "5", "--postbootcommandfile", "/opt/payara/payara-postbootcommand.txt"]
