-- 자료실 설명을 게시글 본문처럼 길게 쓸 수 있도록 확장
--
-- 그동안 varchar(1000)이라 자료를 올릴 때 한두 줄밖에 적지 못했다.
-- 사용 방법이나 주의사항을 게시글처럼 풀어 쓸 수 있게 TEXT로 넓힌다.

ALTER TABLE plaza_library_items MODIFY COLUMN description TEXT;
