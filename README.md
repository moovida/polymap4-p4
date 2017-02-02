# Polymap4 GIS Client

## Building from source

#### Prerequisites

  * Get and install Java8
  * Get and install [Eclipse for RCP and RAP Developers](http://www.eclipse.org/downloads/packages/eclipse-rcp-and-rap-developers/neon2)
  * (optionally) install GitHub Mylyn support from Marketplace

#### Install target platform

  * `wget http://build.mapzone.io/jenkins/job/polymap4-targetplatform/ws/*zip*/polymap4-targetplatform.zip`
  * `unzip polymap4-targetplatform.zip`
  * Eclipse: Preferences->Plugin Developement->Target Platform->Add

#### Get source from GitHub

  * Clone the following repositories from GitHub
    * `git@github.com:Polymap4/polymap4-core.git`
    * `git@github.com:Polymap4/polymap4-rhei.git`
    * `git@github.com:Polymap4/polymap4-rap.git`
    * `git@github.com:Polymap4/polymap4-model.git`
    * `git@github.com:Polymap4/polymap4-p4.git`
  * Import all plugins (wait for Eclipse to downloadjars from maven and compile everything)
  
#### Compile and Launch

After importing all plugins into the workspace Eclipse automatically downloads all dependencies from maven repositories and compiles the sources. If this did not work properly ...

  * ...
