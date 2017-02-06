# Polymap4 GIS Client

## Building from source

#### Prerequisites

  * Get and install Java8
  * Get and install [Eclipse for RCP and RAP Developers](http://www.eclipse.org/downloads/packages/eclipse-rcp-and-rap-developers/neon2)
  * (optionally) install GitHub Mylyn support from Marketplace

#### Install target platform

  * Download [polymap4-targetplatform.zip](http://build.mapzone.io/jenkins/job/polymap4-targetplatform/ws/*zip*/polymap4-targetplatform.zip)
  * `unzip polymap4-targetplatform.zip`
  * Eclipse: Preferences->Plugin Developement->Target Platform->Add

#### Get source from GitHub

  * Clone the following repositories from GitHub
    * `git@github.com:Polymap4/polymap4-core.git`
    * `git@github.com:Polymap4/polymap4-rhei.git`
    * `git@github.com:Polymap4/polymap4-rap.git`
    * `git@github.com:Polymap4/polymap4-model.git`
    * `git@github.com:Polymap4/polymap4-p4.git`
    
  * Import all plugins into workspace

#### Resolve dependencies and compile

Some plugins contain Ant scripts called getjars.build.xml. Those scripts download referenced libraries from Maven repositories. The scripts are configured as Builders of the Eclipse projects. The Ant scripts are executed by Eclipse when manual Build is started.

  * **Manually(!)** build workspace (Ctrl-b)
  * **Manually** build workspace, **again(!)** (help Eclipse to find jars downloaded in previous build)
  * ...until everything is compiled
  
#### Launch

  * Start **P4** launch configuration
  * point your browser at: http://localhost:8080/p4
