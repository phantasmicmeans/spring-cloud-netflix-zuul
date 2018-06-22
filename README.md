Spring Cloud Netflix Zuul
==============

*by S.M.Lee*

![image](https://user-images.githubusercontent.com/20153890/41583901-d1e8aa5c-73e0-11e8-97ff-188fed3cd715.png)

# Overview

대용량 웹 서비스가 증가함에 따라 Microservice Architecture는 선택이 아니라 필수가 되어가고 있다. 기존 Monolithic Architecture와는 달리 Microservice Architecture는 작은 Microservice 단위로 나누어 시스템을 구축한다. 이러한 Microservice는 보통 하나 혹은 여러개의 API로 개발된다. 그렇다면 Microservice가 수백개 혹은 수천개까지 증가하면 수 많은 Endpoint와 공통 로직 등 이를 어떻게 관리해야 할까? 

API Gateway는 수많은 백단의 API Server들의 Endpoint들을 단일화 하고, Authentication, Logging, Monitoring, Routing 등 여러 역할을 수행 할 수 있다. 물론 Netflix의 Zuul은 이러한 기능들을 전부 제공하고 있다. Netflix의 Zuul뿐만 아니라 다른 API Gateway를 사용해도 앞서 말한 기능들을 제공받을 수 있을것이다.

그러나 Zuul은 많은 트래픽과 이로인해 발생하게 될 여러 이슈들에 대해 신속히 대응할 수 있도록 다양한 Filter를 제공한다. 
또한 Netflix의 Zuul은 다른 Netflix OSS의 Component들과 결합이 되었을 때 활용도가 증가한다. 
이 중 가장 매력적인 것은 Client Side Loadbalancer인 Ribbon + Service Discovery & Registry인 Eureka + Fault tolerance library(Circuit Breaker)인 Hystrix 조합을 활용한 Dynamic Routing이지 않을까 싶다. 그리하여 여기서는 Dynamic Routing에 중점을 맞춰서 튜토리얼을 진행해 볼 것이다.

일단 Ribbon, Eureka, Hystrix는 Zuul에 내장되어 있다. Eureka Registry에 등록된 특정 Service의 Server List들을 이용하면 Loadbalancer인 Ribbon에 굳이 Routing시킬 Server List를 따로 등록해 줄 필요가 없게 된다. 이러한 특징은 MSA에서 매우 중요하게 작용한다.

도메인 별 수 많은 API Server로 이루어진 시스템을 관리하기 위해 일일이 Routing 시킬 Server list를 Loadbalancer에 하드코딩하여 등록할 필요가 없고,
새로운 API Server가 계속해서 추가된다 하더라도 Eureka Registry에 등록된 정보를 이용하기에 새롭게 도입된 Server List를 Loadbalancer에 추가할 필요가 없어진다. 

또한 추후에 클러스터를 도입하여 replica를 여러개 만들어 배포 시킨다 할 때에도 Dynamic Routing을 하지 않는다면 Server List를 일일이 입력해야 할것이다.. 별로 바람직하지 않다. 어쨌든 글만으로는 이해가 부족할 수 있다. 실제로 Dynamic Routing을 시켜보자.


# Before We Start 

우리는 고정 ip가 있는 3개 혹은 4개의 Server가 필요하다. 그리고 이 Server들은 다음처럼 활용할 것이다.

1. API Gateway Zuul- (Eureka + Ribbon + Hystrix)
2. micro-service.1(Spring Boot Microservice(Eureka Client))
3. micro-service.2(Spring Boot Microservice(Eureka Client))
4. Eureka-Server

2,3번은 각자 다른 서버에 배포되는 같은 서비스이다. Eureka Server는 1,2,3번 Server중 하나를 이용해 구축해도 된다. 

그럼 먼저 API Gateway를 준비해보자.

# API Gateway - Zuul 

## 1. Dependency

Zuul, Eureka-client 의존성을 추가한다. 또한 Hystrix Dashboard를 추가한다.

```xml		             
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

	<dependency>
		<groupId>org.springframework.cloud</groupId>
		<artifactId>spring-cloud-starter-netflix-zuul</artifactId>
            	<!-- <version>2.0.0.RC1</version> -->
	</dependency>
	
    	<dependency>
        	<groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        
        <dependency>
        	<groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-hystrix-dashboard</artifactId>
            <!-- <version>2.0.0.M2</version> -->
        </dependency>

	<dependency>
    		<groupId>org.springframework.boot</groupId>
            	<artifactId>spring-boot-starter-web</artifactId>
        </dependency>

	<dependency>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-test</artifactId>
		<scope>test</scope>
	</dependency>
```


## 3. Include Zuul

```java
@EnableEurekaClient
@SpringBootApplication
@EnableHystrixDashboard
@EnableZuulProxy
public class ApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}
	
}
```
EurekaClient로 만들기 위해 @EnableEurekaClient annotataion, 그리고 Hystrix Dashboard를 사용하기 위해 @EnableHystrixDashboard annotation을 추가한다.

Hystrix의 장점중 하나는 각 HystrixCommand에 대해 metric을 수집하는 것이다. Hystrix Dashboard는 각 circuit breaker에 대한 상태를 보여준다. 이를 활용하려면 @EnableHystrixDashboard annotation을 main class에 추가하면 된다. 그리고 ~/hystrix 로 접속 시 각 Hystrix Client들의 상태를 볼 수 있다.

그리고 Zuul의 Embedded ReverseProxy를 위한 @EnableZuulProxy annotation을 추가하면 된다. 이 기능을 사용하면 local call은 적절한 service로 forwarding된다. 예를들어 id가 users인 service는 /users의 proxy로 부터 request를 받게 될것이다. 그리고 이 proxy는 Ribbon을 이용해 인스턴스를 찾는다. 그리고 request를 받는 모든 method는 HystrixCommand가 있어야 한다. 따라서 request 실패는 Hystrix metric에 나타난다.(즉 Hystrix Dashboard에 반영된다는 말)

위의 설명이 아직 이해가 안될 수도 있다. 아래에서 다시 나올 내용이니 일단 진행하자.


## 2. Configuration

**1. bootstrap.yml**
```yml	
spring:
    application:
        name: zuul-service
```

application.name을 정한다.

**2. application.yml**

```yml	
zuul:
    ignored-service: "*" 
    prefix: /api
    routes:
        story-service:
            path: /story/**
            serviceId: story-service
            stripPrefix: false
```

설정파일을 잘 보면 쉽게 이해할 수 있다. 먼저 ignored-service와 prefix이다.

* ignored-service => zuul의 라우팅 목록 중 story-service를 제외하고는 ignore한다.*
* prifix => Zuul에 의해 routing되는 모든 service들의 Endpoint를 /api/~ 로 묶는다.

그리고 zuul의 routing 목록 중  /story(zuul.routes.story-service.path)로 들어오는 Http call은 story-servie(zuul.routes.story-service.serviceId) forwarding 된다. 이 serviceId는 우리가 routing 시킬 Eureka-Client의 serviceId를 입력하면 된다. 그렇다면 5번째 라인의 zuul.routes.story-service는 어디서 정의될까? 이제 아래를 보자.

```yml	
hystrix:
  command:
    story-service:
      execution:
        isolation:
          thread:
            timeoutInMilliseconds: 10000

story-service:
    ribbon: 
        eureka:
            enabled: true
        NIWSServerListClassName: com.netflix.niws.loadbalancer.DiscoveryEnabledNIWSServerList
	#ConnectTimeout: 3000
	#ReadTimeout: 3000
        MaxTotalHttpConnections: 500
        MaxConnectionsPerHost: 100
```

위의 zuul.routes.story-service는 아래 story-service에서 정의된다. 그리고 이 story-service의 Server List는 Ribbon을 이용해 찾는다. 이 부분을 잘 봐야 한다. 
그렇다면 이 Ribbon은 story-service가 있는 Server List들을 어디서 가져올까? 분명 yaml파일을 전부 뒤져봐도 Server List는 찾아 볼 수 없다. 그렇다면 지금부터 일일이 Server List를 등록해줘야 할까? 아니다. 앞서 설명했듯이 Eureka Registry로 부터 story-service가 실행중인 Server List를 가져오면 된다. 이렇게 되면 Loadbalancer인 Ribbon에 Server List를 추가할 필요가 없다. **NIWSServerListClassName: com.netflix.niws.loadbalancer.DiscoveryEnabledNIWSServerList** 를 이용해 Eureka 정보를 사용하면 된다는 얘기이다.

**참고**
hystrix.command...timeoutInMilliseconds는 Ribbon의 각 timeout보다 커야 잘 동작한다. 
(RibbonHystrixTimeoutException, Ribbon의 TimeooutException에 대해서는 더 알아봐야 한다.. 다음을 참고하자)
"Zuul, Ribbon and Hystrix timeout confusion" => https://github.com/spring-cloud/spring-cloud-netflix/issues/2606

그리고 Hytrix ThreadPool Isolation.. 이 부분 또한 더 알아봐야 한다..
어쨌든 Dynamic Routing이 우선이므로 일단 넘어가자.

```yml	
eureka:
    client:
        healthcheck: true 
        fetch-registry: true
        serviceUrl:
            defaultZone: ${vcap.services.eureka-service.credentials.uri:http://{Your-Eureka-Server-Address}:8761}/eureka/
        instance:
            instance-id: ${spring.application.name}:${spring.application.instance_id:${random.value}}
            perferIpAddress: true
```

이 부분은 Zuul 또한 Eureka Client로 등록하는 부분이다. 이것으로 Dynamic Routing을 위한 준비는 끝났다. 

이제 Microservice(Spring boot application)를 남은 2개의 서버에서 실행하자.  


Host OS에 설치된 maven을 이용해도 되고, spring boot application의 maven wrapper를 사용해도 된다
(maven wrapper는 Linux, OSX, Windows, Solaris 등 서로 다른 OS에서도 동작한다. 따라서 추후에 여러 서비스들을 Jenkins에서 build 할 때 각 서비스들의 Maven version을 맞출 필요가 없다.)

*A Quick Guide to Maven Wrapper => http://www.baeldung.com/maven-wrapper)*

**a. Host OS의 maven 이용**

```bash
[sangmin@Mint-SM] ~/springcloud-service $ mvn package 
```
&nbsp;

**b. maven wrapper 이용**

```bash
[sangmin@Mint-SM] ~/springcloud-service $ ./mvnw package 
```
&nbsp;

## 8. Execute Spring Boot Application ##

REST API Server가 제대로 구축 되어졌는지 확인해보자.

```bash
[sangmin@Mint-SM] ~/springcloud-service $java -jar target/{your_application_name}.jar
```

Eureka Dashboard를 통해 Client가 제대로 등록 되어졌는지 확인해보자

Check Your Eureka Dashboard 
 * http://{Your-Eureka-Server-Address}:8761 
 * http://{Your-Eureka-Server-Address}:8761/eureka/apps

Client가 Eureka Server에 등록 될 때 약간의 시간이 소요될 수 있다.

&nbsp;

## 9. Dockerizing ## 

구축한 Eureka Client를 docker image를 만들어 볼 차례이다. 먼저 Dockerfile을 작성한다. 

> -       $mvn package 


**Dockerfile**
```
	FROM openjdk:8-jdk-alpine
	VOLUME /tmp
	#ARG JAR_FILE
	#ADD ${JAR_FILE} app.jar
	#dockerfile-maven-plugin으로 docker image를 생성하려면 아래 ADD ~를 주석처리하고, 위 2줄의 주석을 지우면 된다.
	ADD ./target/notice-service-0.0.1.jar app.jar
	ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
```

Dockerfile 작성이 끝났다면 image를 build 하자

**a. dockerfile-maven-plugin 사용시**

```bash
[sangmin@Mint-SM] ~/springcloud-service $ ./mvnw dockerfile:build
```
&nbsp;

**b. docker CLI 사용시**

```bash
[sangmin@Mint-SM] ~/springcloud-service $ docker build -t {your_docker_id}/notice-service:latest
```

이후 docker image가 잘 생성 되었음을 확인하자.

```bash
[sangmin@Mint-SM] ~/springcloud-service $ docker images
REPOSITORY                      TAG                 IMAGE ID            CREATED             SIZE
phantasmicmeans/notice-service  latest              4b79d6a1ed24        2 weeks ago         146MB
openjdk                         8-jdk-alpine        224765a6bdbe        5 months ago        102MB

```
&nbsp;

## 10.  Run Docker Container ##

Docker image를 생성하였으므로 이미지를 실행 시켜보자.

```bash
[sangmin@Mint-SM] ~ $ docker run -it -p 8763:8763 phantasmicmeans/notice-service:latest 
```

이제 Eureka Dashboard를 통해 Client가 제대로 실행 되었는지 확인하면 된다.

&nbsp;


## Conclusion ## 

이상으로 간단한 REST API Server로 구축된 Microservice를 Eureka Client로 구성해 보았다. 다음 장에서는 Eureka Client로 구성된 Microservice에 Hystrix를 적용해 볼 것이다.

**다음 글**
* Hystrix에 대한 이해 & => https://github.com/phantasmicmeans/Spring-Cloud-Netflix-Hystrix
* Spring boot Microservice에 Hystrix적용하기* => https://github.com/phantasmicmeans/Spring-Cloud-Netflix-Hystrix-Tutorial



