-- 기본 카테고리 / sort_order 간격 10 → 사이에 추가 가능, 기타는 항상 마지막
INSERT INTO category (name, sort_order, created_at, updated_at)
VALUES ('소설', 10, NOW(6), NOW(6)),
       ('만화', 20, NOW(6), NOW(6)),
       ('일러스트', 30, NOW(6), NOW(6)),
       ('에세이', 40, NOW(6), NOW(6)),
       ('지식·정보', 50, NOW(6), NOW(6)),
       ('기타', 90, NOW(6), NOW(6));
