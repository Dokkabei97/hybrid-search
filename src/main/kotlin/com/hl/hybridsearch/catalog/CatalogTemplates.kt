package com.hl.hybridsearch.catalog

/**
 * 10개 카테고리의 합성 데이터 사전.
 * 실사용 카탈로그의 축약 근사 — 브랜드·속성·가격 범위를 합리적 분포로 제공한다.
 * 모든 값은 한국어 기본, 모델명·영문 브랜드는 혼재.
 */
object CatalogTemplates {

    val all: List<CategoryTemplate> = listOf(
        fashion(),
        beauty(),
        appliance(),
        digital(),
        grocery(),
        books(),
        furniture(),
        sports(),
        baby(),
        pet(),
    )

    private fun fashion() = CategoryTemplate(
        l1 = "패션의류",
        brands = listOf(
            "유니클로", "자라", "H&M", "무신사 스탠다드", "탑텐",
            "스파오", "폴햄", "에잇세컨즈", "코스", "지오다노",
        ),
        subCategories = mapOf(
            "여성의류" to listOf("블라우스", "원피스", "청바지", "니트", "자켓"),
            "남성의류" to listOf("셔츠", "정장", "티셔츠", "후드집업", "슬랙스"),
            "유아복" to listOf("우주복", "내의", "아우터"),
        ),
        titlePatterns = listOf(
            "{brand} {color} {material} {l3}",
            "{brand} {season} 신상 {l3} {size}",
            "{brand} 베이직 {l3} {color}",
        ),
        descriptionPatterns = listOf(
            "{brand}의 {color} {l3}입니다. {material} 소재로 {season}에 적합하며, 편안한 착용감을 제공합니다.",
            "{l3}을(를) 찾고 계시다면 이 제품을 추천합니다. {material} 100%, {color} 컬러, {size} 사이즈 지원.",
        ),
        attributes = mapOf(
            "color" to listOf("블랙", "화이트", "네이비", "베이지", "카키", "차콜", "그레이", "버건디"),
            "size" to listOf("XS", "S", "M", "L", "XL", "XXL"),
            "material" to listOf("면", "폴리에스터", "울", "린넨", "캐시미어", "데님"),
            "gender" to listOf("남성", "여성", "유니섹스"),
            "season" to listOf("봄", "여름", "가을", "겨울", "간절기"),
        ),
        priceRange = 9_900L..250_000L,
        commonTags = listOf("신상", "베스트", "세일", "기본템", "데일리"),
    )

    private fun beauty() = CategoryTemplate(
        l1 = "뷰티",
        brands = listOf(
            "설화수", "아모레퍼시픽", "이니스프리", "에뛰드", "라네즈",
            "헤라", "닥터자르트", "메디힐", "피지오겔", "더페이스샵",
        ),
        subCategories = mapOf(
            "스킨케어" to listOf("토너", "에센스", "크림", "클렌저", "마스크팩"),
            "메이크업" to listOf("립스틱", "파운데이션", "아이섀도우", "마스카라"),
            "바디" to listOf("바디워시", "바디로션", "핸드크림"),
        ),
        titlePatterns = listOf(
            "{brand} {l3} {volume} {skinType}용",
            "{brand} {ingredient} {l3} {volume}",
            "{brand} {l3} - {skinType} 피부 추천",
        ),
        descriptionPatterns = listOf(
            "{brand}의 {l3}입니다. 주요 성분은 {ingredient}이며 {skinType} 피부에 적합합니다. 용량 {volume}.",
            "매일 사용 가능한 {l3}. {ingredient} 함유, 순한 성분으로 민감성 피부도 안심.",
        ),
        attributes = mapOf(
            "volume" to listOf("30ml", "50ml", "100ml", "150ml", "200ml"),
            "skinType" to listOf("건성", "지성", "복합성", "민감성", "모든 피부"),
            "ingredient" to listOf("히알루론산", "나이아신아마이드", "비타민C", "레티놀", "세라마이드"),
        ),
        priceRange = 7_000L..220_000L,
        commonTags = listOf("민감성", "저자극", "비건", "무향", "수분"),
    )

    private fun appliance() = CategoryTemplate(
        l1 = "가전",
        brands = listOf(
            "삼성", "LG", "쿠쿠", "위니아", "캐리어",
            "코웨이", "위닉스", "다이슨", "샤오미", "브라운",
        ),
        subCategories = mapOf(
            "주방가전" to listOf("냉장고", "전자레인지", "에어프라이어", "커피머신", "전기밥솥"),
            "생활가전" to listOf("세탁기", "건조기", "청소기", "공기청정기"),
            "냉난방" to listOf("에어컨", "히터", "선풍기", "제습기"),
        ),
        titlePatterns = listOf(
            "{brand} {capacity} {l3} {energyGrade}",
            "{brand} 프리미엄 {l3} {capacity}",
            "{brand} {l3} {power}W",
        ),
        descriptionPatterns = listOf(
            "{brand}의 {capacity} {l3}. 에너지 소비효율 {energyGrade}, 정격소비전력 {power}W. 최신 기술로 조용하고 효율적인 사용 가능.",
            "넓은 공간에도 충분한 {capacity} 용량. {brand} 특허 기술로 {energyGrade}등급 달성.",
        ),
        attributes = mapOf(
            "capacity" to listOf("200L", "300L", "500L", "10kg", "17평", "24평"),
            "power" to listOf("800", "1200", "1500", "1800", "2200"),
            "energyGrade" to listOf("1등급", "2등급", "3등급"),
        ),
        priceRange = 80_000L..3_500_000L,
        commonTags = listOf("에너지효율", "프리미엄", "스마트", "설치포함"),
    )

    private fun digital() = CategoryTemplate(
        l1 = "디지털",
        brands = listOf(
            "Apple", "삼성", "LG", "레노버", "Dell",
            "HP", "ASUS", "소니", "Bose", "샤오미",
        ),
        subCategories = mapOf(
            "노트북" to listOf("게이밍 노트북", "울트라북", "2in1", "크롬북"),
            "스마트폰" to listOf("플래그십", "미들레인지", "보급형"),
            "오디오" to listOf("무선이어폰", "헤드폰", "블루투스 스피커"),
            "주변기기" to listOf("키보드", "마우스", "모니터"),
        ),
        titlePatterns = listOf(
            "{brand} {l3} {chipset} {ram} {storage}",
            "{brand} {l3} {screenSize} {color}",
            "{brand} {l3} {model} - {storage}",
        ),
        descriptionPatterns = listOf(
            "{brand} {l3} 신모델. {chipset} 칩셋, {ram} 메모리, {storage} 저장공간. 화면 크기 {screenSize}.",
            "고성능 {l3}. {chipset}, {ram}, {storage} 구성으로 무거운 작업도 쾌적.",
        ),
        attributes = mapOf(
            "chipset" to listOf("M3 Pro", "M2", "Snapdragon 8 Gen 3", "Intel Core i7", "AMD Ryzen 7", "Exynos 2400"),
            "ram" to listOf("8GB", "16GB", "24GB", "32GB"),
            "storage" to listOf("256GB", "512GB", "1TB", "2TB"),
            "screenSize" to listOf("6.1인치", "6.7인치", "13인치", "14인치", "16인치", "27인치"),
            "color" to listOf("스페이스그레이", "실버", "미드나잇", "블루", "팬텀블랙"),
            "model" to listOf("2024", "2025 Edition", "Pro", "Max", "Ultra"),
        ),
        priceRange = 49_000L..6_800_000L,
        commonTags = listOf("최신", "프로모션", "공식판매", "신제품"),
    )

    private fun grocery() = CategoryTemplate(
        l1 = "식품",
        brands = listOf(
            "풀무원", "CJ", "오뚜기", "농심", "샘표",
            "동원", "해태", "롯데", "남양", "매일",
        ),
        subCategories = mapOf(
            "신선식품" to listOf("과일", "채소", "육류", "수산"),
            "가공식품" to listOf("라면", "과자", "통조림", "냉동식품"),
            "음료" to listOf("생수", "커피", "주스", "탄산음료"),
        ),
        titlePatterns = listOf(
            "{brand} {l3} {weight} {origin}",
            "{brand} {flavor} {l3} {weight}",
            "{brand} 프리미엄 {l3} {allergen}",
        ),
        descriptionPatterns = listOf(
            "{brand}의 {l3}. 원산지: {origin}, 용량 {weight}. {allergen}.",
            "맛있는 {flavor} {l3}. 유통기한이 긴 {weight} 용량.",
        ),
        attributes = mapOf(
            "weight" to listOf("100g", "200g", "500g", "1kg", "2kg", "500ml", "1L"),
            "origin" to listOf("국내산", "미국산", "호주산", "태국산", "베트남산"),
            "allergen" to listOf("견과류 포함", "밀 포함", "대두 포함", "알러지 성분 없음"),
            "flavor" to listOf("오리지널", "매운맛", "순한맛", "달콤한맛"),
        ),
        priceRange = 1_500L..85_000L,
        commonTags = listOf("유기농", "무첨가", "로컬푸드", "HACCP"),
    )

    private fun books() = CategoryTemplate(
        l1 = "도서",
        brands = listOf(
            "민음사", "창비", "문학동네", "김영사", "한빛미디어",
            "인사이트", "위키북스", "길벗", "에이콘출판사", "책세상",
        ),
        subCategories = mapOf(
            "소설" to listOf("한국소설", "외국소설", "추리", "SF", "로맨스"),
            "IT" to listOf("프로그래밍", "데이터분석", "AI/머신러닝", "클라우드"),
            "경제경영" to listOf("경제일반", "자기계발", "투자"),
            "인문" to listOf("철학", "역사", "사회학"),
        ),
        titlePatterns = listOf(
            "{title} - {author} ({brand})",
            "{topic} 완벽 가이드 ({brand})",
            "{author}의 {l3}",
        ),
        descriptionPatterns = listOf(
            "{author}의 {l3}. {topic}을(를) 다룬 {pages}페이지 분량. ISBN {isbn}.",
            "{brand}에서 펴낸 {l3}. {topic}에 대한 깊이 있는 통찰을 제공합니다.",
        ),
        attributes = mapOf(
            "author" to listOf("김영하", "한강", "박상영", "정보라", "최재천", "유발 하라리", "로버트 마틴", "도널드 커누스"),
            "pages" to listOf("180", "240", "320", "420", "580", "720"),
            "topic" to listOf("일상", "사랑", "AI", "클라우드", "주식", "부동산", "철학", "역사"),
            "title" to listOf("기억의 방", "여름의 문법", "도메인 주도 설계", "클린 코드", "사피엔스"),
            "isbn" to listOf("979-11-XXX", "978-89-YYY"),
        ),
        priceRange = 9_800L..68_000L,
        commonTags = listOf("베스트셀러", "신간", "추천도서", "eBook 동시"),
    )

    private fun furniture() = CategoryTemplate(
        l1 = "가구",
        brands = listOf(
            "이케아", "한샘", "에넥스", "현대리바트", "까사미아",
            "시몬스", "에이스침대", "일룸", "퍼시스", "오늘의집",
        ),
        subCategories = mapOf(
            "거실" to listOf("소파", "TV장", "거실장", "러그"),
            "침실" to listOf("침대", "매트리스", "옷장", "화장대"),
            "주방" to listOf("식탁", "의자", "수납장"),
            "서재" to listOf("책상", "책장", "사무의자"),
        ),
        titlePatterns = listOf(
            "{brand} {material} {l3} {width}x{depth}",
            "{brand} 북유럽 스타일 {l3} {color}",
            "{brand} {l3} - {material} 원목",
        ),
        descriptionPatterns = listOf(
            "{brand}의 {material} {l3}. 크기 {width}x{depth}x{height}. 조립 난이도 중간.",
            "공간을 넓어 보이게 해주는 {color} {l3}. {material} 소재.",
        ),
        attributes = mapOf(
            "material" to listOf("원목", "MDF", "철제", "패브릭", "가죽"),
            "width" to listOf("60cm", "90cm", "120cm", "160cm", "200cm"),
            "depth" to listOf("40cm", "50cm", "60cm", "80cm"),
            "height" to listOf("40cm", "75cm", "120cm", "180cm"),
            "color" to listOf("내추럴", "월넛", "화이트", "블랙", "그레이"),
        ),
        priceRange = 29_000L..1_800_000L,
        commonTags = listOf("북유럽", "모던", "빈티지", "조립식"),
    )

    private fun sports() = CategoryTemplate(
        l1 = "스포츠",
        brands = listOf(
            "나이키", "아디다스", "뉴발란스", "아식스", "푸마",
            "언더아머", "밀레", "노스페이스", "컬럼비아", "블랙야크",
        ),
        subCategories = mapOf(
            "러닝" to listOf("러닝화", "러닝웨어", "러닝양말"),
            "등산" to listOf("등산화", "등산복", "백팩"),
            "헬스" to listOf("트레이닝복", "요가매트", "덤벨"),
            "구기" to listOf("축구화", "농구화", "라켓"),
        ),
        titlePatterns = listOf(
            "{brand} {l3} {model} {size}",
            "{brand} {sport} 전용 {l3}",
            "{brand} {l3} - {skillLevel}용",
        ),
        descriptionPatterns = listOf(
            "{brand}의 {l3}. {sport} 전용으로 설계되었으며 {skillLevel} 수준에 적합합니다.",
            "가볍고 편안한 {l3}. {brand}의 최신 기술 적용.",
        ),
        attributes = mapOf(
            "size" to listOf("230", "240", "250", "260", "270", "280", "S", "M", "L", "XL"),
            "sport" to listOf("러닝", "등산", "요가", "필라테스", "축구", "농구", "테니스"),
            "skillLevel" to listOf("초보자", "중급자", "상급자"),
            "model" to listOf("Pro", "Elite", "Basic", "Performance"),
        ),
        priceRange = 19_000L..480_000L,
        commonTags = listOf("경량", "통기성", "쿠셔닝", "방수"),
    )

    private fun baby() = CategoryTemplate(
        l1 = "유아/출산",
        brands = listOf(
            "퍼기", "맘큐", "하기스", "유한킴벌리", "다이치",
            "베이비케어", "아가방", "보령메디앙스", "궁중비책", "리틀원",
        ),
        subCategories = mapOf(
            "기저귀" to listOf("팬티형 기저귀", "밴드형 기저귀", "수영 기저귀"),
            "분유" to listOf("1단계 분유", "2단계 분유", "3단계 분유"),
            "장난감" to listOf("촉감놀이", "블록", "음악완구"),
            "의류" to listOf("배냇저고리", "우주복", "내의"),
        ),
        titlePatterns = listOf(
            "{brand} {l3} {ageRange} {size}",
            "{brand} {safety} {l3} {count}개입",
            "{brand} {l3} - {ageRange}",
        ),
        descriptionPatterns = listOf(
            "{brand}의 {l3}. {ageRange} 대상, {safety} 검증 완료. {count}개입.",
            "안심하고 쓸 수 있는 {safety} {l3}. {ageRange}에 적합.",
        ),
        attributes = mapOf(
            "ageRange" to listOf("0-3개월", "3-6개월", "6-12개월", "12-24개월", "2-3세"),
            "size" to listOf("NB", "S", "M", "L", "XL", "XXL"),
            "safety" to listOf("KC 인증", "FDA 인증", "친환경 소재", "무형광"),
            "count" to listOf("30", "60", "90", "120"),
        ),
        priceRange = 5_900L..150_000L,
        commonTags = listOf("안전인증", "순면", "저자극", "신생아"),
    )

    private fun pet() = CategoryTemplate(
        l1 = "반려동물",
        brands = listOf(
            "로얄캐닌", "힐스", "오리젠", "아카나", "퓨리나",
            "아이펫", "지위픽", "내추럴발란스", "뉴트로", "웰니스",
        ),
        subCategories = mapOf(
            "강아지" to listOf("건식사료", "습식사료", "간식", "장난감"),
            "고양이" to listOf("건식사료", "습식사료", "모래", "캣타워"),
            "소동물" to listOf("사료", "하우스"),
        ),
        titlePatterns = listOf(
            "{brand} {petType} {l3} {weight}",
            "{brand} {lifeStage} {petType} {l3}",
            "{brand} {breed} 전용 {l3}",
        ),
        descriptionPatterns = listOf(
            "{brand}의 {petType}용 {l3}. {lifeStage} 대상, {weight} 용량.",
            "{breed}에 특화된 {l3}. {lifeStage} 단계 영양 균형.",
        ),
        attributes = mapOf(
            "petType" to listOf("강아지", "고양이", "토끼", "햄스터", "앵무새"),
            "weight" to listOf("400g", "1kg", "2kg", "5kg", "10kg"),
            "lifeStage" to listOf("퍼피", "주니어", "어덜트", "시니어"),
            "breed" to listOf("소형견", "중형견", "대형견", "단모종", "장모종"),
        ),
        priceRange = 6_000L..180_000L,
        commonTags = listOf("유기농", "그레인프리", "수의사추천", "기능성"),
    )
}
