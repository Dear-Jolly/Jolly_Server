DELETE FROM correction_segments
WHERE feedback_id IN (
    SELECT feedback_id
    FROM feedbacks
    WHERE model = 'seed-mock'
);

DELETE FROM feedback_tips
WHERE feedback_id IN (
    SELECT feedback_id
    FROM feedbacks
    WHERE model = 'seed-mock'
);

DELETE FROM feedbacks
WHERE model = 'seed-mock';

DELETE FROM letters
WHERE content IN (
    'This morning I woke up late and ran to the station. I almost miss the train but the driver waited a few seconds for me.',
    'I stayed up late to watch a long movie, so I slept only four hours. I feel so tired today and I could not focus at work.',
    'I met my old friend at a ordinary cafe near my house. We talk about our new jobs for almost two hours.',
    'I go running along the river this morning. The air was very cold but it make me feel alive again.',
    'Yesterday was my birthday. My family got me a small cake which had only one candle on it.',
    'I tried to cook pasta by myself for the first time. It was too salty but I eat all of it anyway.'
);
