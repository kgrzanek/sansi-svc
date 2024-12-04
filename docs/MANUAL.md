# How to use telsos-svc as an upstream

The following instruction assumes we want to create a new service called *\<service-name\>-svc*.



1. Go to GitHub and create a new empty repository, https://github.com/\<github-username\>/\<service-name\>-svc

   

2. Clone your repo

   ```bash
   git clone https://github.com/<github-username>/<service-name>-svc
   cd <service-name>-svc
   ```

   

3. Add telsos-svc repository as your upstream:

   ```bash
   git remote add upstream https://github.com/kongra/telsos-svc
   ```



4. Pull upstream

   ```bash
   git pull upstream master
   ```

   or

   ```bash
   git rebase upstream/master
   ```

   

   Next:

   ```bash
   git status
   On branch master
   Your branch is based on 'origin/master', but the upstream is gone.
     (use "git branch --unset-upstream" to fixup)
   
   nothing to commit, working tree clean
   ```

   So you need to:

   ```bash
   git branch --unset-upstream
   ```

   and then

   ```bash
   git status
   On branch master
   nothing to commit, working tree clean
   ```

   Finally:

   ```bash
   git push -u origin master
   ```

   

5. Apply changes to your new \<service-name\>-svc. As of writing this I had to affect the following files:

   ```bash
   README.md
   build.clj
   db/init/setup.sql
   docker-compose.yml
   makefile
   src/main/clj/telsos/svc/config/postgres.clj
   src/main/clj/telsos/svc/core.clj
   src/main/sql/test1.sql
   test/telsos/svc/core_test.clj
   ```

   

6. Execute

   ```bash
   make up # Running docker compose
   make uberjar
   make run
   make clean
   make kaocha-it
   ```

   Have fun!
