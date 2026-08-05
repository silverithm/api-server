-- 커뮤니티 메뉴 개편: 실무 Q&A 게시판을 실무팁으로 전환하고,
-- 평가후기·실무팁 글에 시설 유형(주간보호/방문요양·목욕/요양원) 카테고리를 추가한다.
ALTER TABLE plaza_posts ADD COLUMN category VARCHAR(20) NULL AFTER board;

UPDATE plaza_posts SET board = 'TIP' WHERE board = 'QNA';
