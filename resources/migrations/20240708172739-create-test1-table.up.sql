create table test1 (
  id  bigserial not null,
  val bigint    not null,

  primary key(id)
);
--;;
insert into test1(val) values(0);
--;;
insert into test1(val) values(1);
--;;
insert into test1(val) values(2);
