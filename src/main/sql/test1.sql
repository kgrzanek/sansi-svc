-- :name all-test1-data :? :*
select id, val from sansi.test1 limit 100;

-- :name inc-test1-val! :! :n
update test1 set val = val + 1 where id = :id;
