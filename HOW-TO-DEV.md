# Setup
Download the latest version of IntelliJ IDEA (https://www.jetbrains.com/idea/download/)
Configure to the correct version of Node.js (https://nodejs.org/en/download/)
Configure to the correct version of Java (https://www.oracle.com/java/technologies/javase-downloads.html)

# Configuration
1. In the top-bar, go to the "Build" menu and select "Build Artifacts...".
2. There will be 2 options, nodejt400:jar and nodejt400:jar-node_modules. Hover
over the nodejt400:jar-node_modules and click the "Edit..." button.
3. Change the output directory to you Socket node modules targeting the /java/lib structure.

# Usage

### Java Changes
1. Make the desired changes and save.
2. In the top bar, go to the "Build" menu and select "Build Artifacts...". 
3. There will be 2 options, nodejt400:jar and nodejt400:jar-node_modules. Hover over the nodejt400:jar-node_modules and click the "Build" button.

### Node Changes
1. Make the desired changes and save.
2. In the top bar, go to the "Build" menu and select "Build Artifacts...". 
3. There will be 2 options, nodejt400:jar and nodejt400:jar-node_modules. Hover over the nodejt400:jar and click the "Build" button.
    a. 5 files will be created in the "/out/artifacts/nodejt400_jar/lib/" directory.
    (hsqldb.jar, json-simple-1.1.1.jar, jt400.jar, jt400wrap.jar, junit4.jar)
4. Run `npm run build` to build the node-jt400 module.
5. Copy the dist folder to the "node-jt400" node module in Socket.

# Publishing changes
1. Make the desired changes and save.
2. In the top bar, go to the "Build" menu and select "Build Artifacts...". 
3. There will be 2 options, nodejt400:jar and nodejt400:jar-node_modules. Hover over the nodejt400:jar and click the "Build" button.
4. Run `npm i` to install necessary dependencies.
5. Run `npm run test` to ensure nothing has broke.
6. Run `npm version patch` to update the version number
7. Run `git add .` to add all changes
8. Run `git commit -m "Update to version X.X.X"` to commit the changes
9. Run `git push` to push the changes to the remote repository
10. Run `npm publish` to publish the changes to the npm repository
