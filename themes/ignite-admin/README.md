```
**************************************************************************

   _____    _____   _   _____    _    ____      _____   _____    _____
  / ____|  / ____| | | |  __ \  | |  / __ \    |  ___| |  __ \  |  __ \
 | (___   | |      | | | |__) | | | | |  | |   | |___  | |__) | | |__) |
  \___ \  | |      | | |  ___/  | | | |  | |   |  ___| |  _  /  |  ___/
  ____) | | |____  | | | |      | | | |__| |   | |___  | | \ \  | |
 |_____/   \_____| |_| |_|      |_|  \____/    |_____| |_|  \_\ |_|

                    https://www.scipioerp.com
                    
                    Ignite Admin (Bootstrap v4) - Theme

**************************************************************************
```
![Ignite Admin Theme - Bootstrap v4](http://shop.scipioerp.com/images/products/IGNITE_ADM_THM_VIR/large.png)

# Ignite Admin Theme (Bootstrap v4)
##  General
This is a backoffice theme implemented using the Bootstrap v4 CSS framework and based on Core UI (http://coreui.io/index.html#features). 
The ignite themes are the latest in frontend-development. Based on the latest version of the Bootstrap html framework (Bootstrap v4), these stylish themes are built for a responsive, mobile-first applications. 
Bootstrap 4 already realizes the flexbox model, which gibes greater control over the overall page layout. 

The theme is highly customizable and includes all the sources, templating toolkit overrides & class mappings as an easy start into development.
Frontend-development is done using SASS, Bower and GULP. Additional frontend libraries, such as Chart.js, datatables JS, jquery and momentjs are included in this package. 

Please be aware that the theme may receive frequent updates. 
To get started, the following information may be of interest to you:

## Directory structure
* Default load style sheets directory: 
  webapp/ignite/css/
* SCIPIO will load a base set of class & html definitions located here:
  htmlVariables.groovy - ofbiz_foundation/framework/common/webcommon/includes/scipio/lib/standard
  
  These can be (and for the purpose of this theme will be) overriden in the following file:
  themeStyles.groovy - ofbiz_foundation/themes/bootstrap/includes

## What's included
* Theme component
* Clean Bootstrap CSS (SASS) and Javascript sources
* Additional Scipio ERP customizations (all factored out for convenience)
* Scipio ERP templating toolkit overrides
* Scipio ERP class mappings
  
## Installation
### Theme installation
Scipio ERP provides a simple bash script to manage all addons. Detailed installation instructions are available on the [Scipio ERP website](https://www.scipioerp.com/community/end-user/addons/). For help, simply type on the console:

```
./git-addons help

```

### Frontend Development
#### Prerequisits
To setup system to compile sass files, the following packages/commands are needed (ubuntu/debian/mint):

```
sudo apt-get install ruby ruby-dev ruby-full nodejs nodejs-dev npm
```

On windows download and use the [ruby installer](https://rubyinstaller.org/) and [nodejs](https://nodejs.org/en/) installers.  


#### Installation
Go to @component-name/webapp/@component-name/ and run

```
npm install
npm install -g bower
bower install
```

If npm install throws an error about a missing node-sass url, run 
```
npm i gulp-sass@latest --save-dev
```

and retry the install process.

#### Sass (CSS) Compilation 
To start watching for less changes to auto compile to css, go to:

@component-name/webapp/@component-name/

and run:
```
gulp
```

This will open a new browser during development. Afterwards, run the following command to repackage for proper distribution:

```
gulp build:dist
```


#### Bower Update 
To update the dependencies, run the bower update command from @component-name/webapp/@component-name/:
```
bower update
```