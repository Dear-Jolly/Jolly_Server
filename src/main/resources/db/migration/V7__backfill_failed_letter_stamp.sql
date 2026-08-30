UPDATE letters
SET stamp_id = (SELECT stamp_id FROM stamps WHERE name = 'fail')
WHERE retry_count >= 1
  AND status <> 'FEEDBACK_COMPLETED'
  AND EXISTS (SELECT 1 FROM stamps WHERE name = 'fail');
