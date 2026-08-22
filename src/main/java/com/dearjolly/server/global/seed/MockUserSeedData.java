package com.dearjolly.server.global.seed;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MockUserSeedData {
    // 편지 본문은 시드 재실행 시 같은 편지인지 가리는 식별자로도 쓰인다. 본문을 고치면 새 편지가 하나 더 생긴다.
    public static final List<MockLetterSeed> LETTERS = List.of(
            MockLetterSeed.pending(
                    0,
                    "This morning I woke up late and ran to the station. "
                            + "I almost miss the train but the driver waited a few seconds for me."
            ),
            MockLetterSeed.completed(
                    1,
                    "I stayed up late to watch a long movie, so I slept only four hours. "
                            + "I feel so tired today and I could not focus at work.",
                    "I stayed up late watching a long movie, so I slept only four hours. "
                            + "I feel so tired today and I could not focus at work.",
                    List.of(
                            "stay up late 뒤에는 목적이 아니라 '무엇을 하며' 늦게까지 있었는지가 오니까 watching 처럼 -ing 형이 자연스러워요!",
                            "could not focus 는 잘 쓰셨어요. 이어서 focus on my work 처럼 on 을 붙이면 더 또렷해져요!"
                    ),
                    "잠_침대_늦잠",
                    false
            ),
            MockLetterSeed.completed(
                    3,
                    "I met my old friend at a ordinary cafe near my house. "
                            + "We talk about our new jobs for almost two hours.",
                    "I met my old friend at an ordinary cafe near my house. "
                            + "We talked about our new jobs for almost two hours.",
                    List.of(
                            "a 와 an 은 뒤에 오는 소리로 정해져요. ordinary 는 모음 소리로 시작하니 an ordinary 가 맞아요!",
                            "지난 일을 적을 때는 talk 가 아니라 talked 처럼 과거형으로 통일해 주세요!"
                    ),
                    "커피잔",
                    true
            ),
            MockLetterSeed.completed(
                    5,
                    "I go running along the river this morning. "
                            + "The air was very cold but it make me feel alive again.",
                    "I went running along the river this morning. "
                            + "The air was very cold but it made me feel alive again.",
                    List.of(
                            "this morning 처럼 이미 지나간 시간을 말할 때는 go 가 아니라 went 를 써요!",
                            "한 문장 안에서 was 로 과거를 열었으면 뒤의 make 도 made 로 맞춰야 해요!",
                            "feel alive 는 아주 좋은 표현이에요. 다음엔 feel alive again 처럼 부사를 붙여 보세요!"
                    ),
                    "달리기_런닝_운동",
                    true
            ),
            MockLetterSeed.completed(
                    8,
                    "Yesterday was my birthday. My family got me a small cake which had only one candle on it.",
                    "Yesterday was my birthday. My family got me a small cake that had only one candle on it.",
                    List.of(
                            "which 는 덧붙이는 정보에, that 은 앞말을 꼭 집어 한정할 때 써요. 여기서는 that 이 자연스러워요!"
                    ),
                    "케이크_생일",
                    true
            ),
            MockLetterSeed.pending(
                    12,
                    "I tried to cook pasta by myself for the first time. "
                            + "It was too salty but I eat all of it anyway."
            )
    );
}
