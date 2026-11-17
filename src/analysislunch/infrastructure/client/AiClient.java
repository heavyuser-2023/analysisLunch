package analysislunch.infrastructure.client;

import java.io.File;
import java.io.IOException;

import analysislunch.domain.model.MenuInfo;

/** 메뉴 인식, 식판 이미지 생성, 칼로리 분석의 공급자 공통 경계. */
public interface AiClient {
    /** 메뉴 이미지에서 날짜와 메뉴를 추출합니다. */
    MenuInfo extractMenuInfo(File imageFile) throws IOException;

    /** 메뉴에 해당하는 식판 이미지 파일을 생성합니다. */
    File generateFoodImage(String menuText) throws IOException;

    /** 음식 이미지와 메뉴를 바탕으로 칼로리 분석을 반환합니다. */
    String analyzeCalories(File imageFile, String menuText) throws IOException;
}
