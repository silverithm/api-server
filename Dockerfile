FROM eclipse-temurin:17-jre
ENV TZ=Asia/Seoul

# 아이폰 HEIC 사진을 JPEG로 바꾸기 위한 heif-convert(libheif-examples).
# 크롬·엣지·파이어폭스는 HEIC를 렌더링하지 못해 웹에서 사진이 보이지 않는다.
# JVM에서 HEIC를 읽는 라이브러리(nightmonkeys)는 JDK 22를 요구해 Java 17인 이 프로젝트에서는
# 로드되자마자 스스로 등록을 해제한다. 그래서 패키지 두 개(약 10MB)로 해결한다.
# libheif-plugin-libde265가 없으면 heif-convert는 설치돼 있어도 HEIC를 디코딩하지 못한다.
# 플러그인 패키지는 베이스 이미지가 옛 우분투일 때 존재하지 않을 수 있다(그 시절 libheif는
# libde265를 내장했다). 없어도 배포가 멈추지 않도록 실패를 허용하고, 실제 디코더 목록을 빌드 로그에 남긴다.
RUN apt-get update \
 && apt-get install -y --no-install-recommends libheif-examples \
 && (apt-get install -y --no-install-recommends libheif-plugin-libde265 \
     || echo "[build] libheif-plugin-libde265 없음 - libheif 내장 디코더를 사용합니다") \
 && rm -rf /var/lib/apt/lists/* \
 && heif-convert --list-decoders

CMD ["./gradlew", "clean", "build"]
COPY ./build/libs/vehicle-placement-system-0.0.1-SNAPSHOT.jar /app.jar
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-Duser.timezone=Asia/Seoul", "-jar","/app.jar"]
