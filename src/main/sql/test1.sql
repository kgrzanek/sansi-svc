-- :name all-test1-data :? :*
select id, val from telsos.test1;

-- :name inc-test1-val! :! :n
update test1 set val = val + 1 where id = :id;
