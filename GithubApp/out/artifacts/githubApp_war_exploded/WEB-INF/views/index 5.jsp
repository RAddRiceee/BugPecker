<%--
  Created by IntelliJ IDEA.
  User: apple
  Date: 2020/5/9
  Time: 7:33 PM
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html lang="en">
<head>
    <meta charset="utf-8">
    <title>Booom!</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">

    <!-- The main CSS file -->
    <link href="/css/main.css" rel="stylesheet">
    <link rel="stylesheet" href="http://cdn.bootcss.com/bootstrap/3.3.0/css/bootstrap.min.css">

    <!-- CSS file for your custom modifications -->
    <!--<link href="css/custom.css" rel="stylesheet">-->

    <!--<link rel="shortcut icon" href="images/favicon.ico">-->

    <!-- HTML5 shim, for IE6-8 support of HTML5 elements. All other JS at the end of file. -->
    <!--[if lt IE 9]>
    <script src="../statics/js/html5shiv.js"></script>
    <![endif]-->
</head>


<!-- Scrollspy set in the body -->
<body id="home" data-spy="scroll" data-target=".main-nav" data-offset="73">

<!--//////////////////////////////////////// NEWSLETTER SECTION ////////////////////////////////////////-->

<section id="newsletter">

    <div class="container">
        <div class="row">

            <div class="col-md-12">
                <!--<p class="lead">Bug Localization!</p>-->
                <h1 style="color: white">BugPecker Tool</h1>
            </div>

            <!--<div class="col-md-6">-->

            <!--////////// Newsletter Form //////////-->
            <!--<form id="newsletter-signup">-->
            <!--<div class="input-group">-->
            <!--<input type="text" name="e-mail" id="e-mail" class="form-control input-hg">-->
            <!--<span class="input-group-btn">-->
            <!--<button class="btn btn-inverse btn-hg" type="submit" name="submit">Sign Up</button>-->
            <!--</span>-->
            <!--</div>&lt;!&ndash; /input-group &ndash;&gt;-->
            <!--</form>-->
            <!--////////// end of Newsletter Form ///////////-->

            <!--<div id="error-info"></div>&lt;!&ndash; Error notification for newsletter signup form &ndash;&gt;-->

            <!--</div>-->

        </div><!-- /row -->
    </div><!-- /container -->

</section>

<!--//////////////////////////////////////// end NEWSLETTER SECTION ////////////////////////////////////////-->

<!--/////////////////////////////////////// FEATURES SECTION ////////////////////////////////////////-->

<section id="features">

    <div class="container">

        <header>
            <h1>Repository Features</h1>
            <p class="lead">Your repositories are initialized.</p>
        </header>

        <div class="row">

            <div class="col-md-3 text-center">
                <div class="feature-icon">
                    <div>
                        <h3>${codeData.versionNum}</h3><span class="glyphicon glyphicon-file"></span>
                    </div>
                </div>
                <h4>Version Number</h4>
                <p>code entities in repository are extracted from several different commit versions</p>
            </div>

            <!-- Feature Item 2 -->
            <div class="col-md-3 text-center">
                <div class="feature-icon">
                    <h3>${codeData.methodNum}</h3><span class="glyphicon glyphicon-list-alt"></span>
                </div>
                <h4>Method Number</h4>
                <p>method entities are main and key code entities in repository</p>
            </div>

            <!-- Feature Item 3 -->
            <div class="col-md-3 text-center">
                <div class="feature-icon">
                    <h3>${codeData.callRelNUm}</h3><span class="glyphicon glyphicon-paperclip"></span>
                </div>
                <h4>CallRel Number</h4>
                <p>call relations between method entities are extracted and saved in repository</p>
            </div>

            <!-- Feature Item 4 -->
            <div class="col-md-3 text-center">
                <div class="feature-icon">
                    <h3>${codeData.simRelNum}</h3><span class="glyphicon glyphicon-asterisk"></span>
                </div>
                <h4>SimRel Number</h4>
                <p>similar relations between method entities are calculated and saved in repository</p>
            </div>
        </div>
        <center><a href="https://github.com/apps/bugpecker"><button class="btn btn-inverse btn-hg" type="submit" name="submit">Back to github</button></a></center>

        <!-- /row -->
    </div><!-- /container -->

</section>

<!--/////////////////////////////////////// end FEATURES SECTION ////////////////////////////////////////-->


<!--//////////////////////////////////////// JAVASCRIPT LOAD ////////////////////////////////////////-->

<!-- Feel free to remove the scripts you are not going to use -->
<%--<script src="../statics/js/jquery-1.8.3.min.js"></script>--%>
<%--<script src="../statics/js/jquery-ui-1.10.3.custom.min.js"></script>--%>
<%--<script src="../statics/js/jquery.ui.touch-punch.min.js"></script>--%>
<%--<script src="../statics/js/bootstrap.min.js"></script>--%>
<%--<script src="../statics/js/jquery.isotope.min.js"></script>--%>
<%--<script src="../statics/js/jquery.magnific-popup.js"></script>--%>
<%--<script src="../statics/js/jquery.fitvids.min.js"></script>--%>
<%--<script src="assets/twitter/jquery.tweet.js"></script>--%>
<%--<script src="../statics/js/bootstrap-select.js"></script>--%>
<%--<script src="../statics/js/bootstrap-switch.js"></script>--%>
<%--<script src="../statics/js/flatui-checkbox.js"></script>--%>
<%--<script src="../statics/js/flatui-radio.js"></script>--%>
<%--<script src="../statics/js/jquery.tagsinput.js"></script>--%>
<%--<script src="../statics/js/jquery.placeholder.js"></script>--%>
<%--<script src="../statics/js/custom.js"></script>--%>

<!--//////////////////////////////////////// end JAVASCRIPT LOAD ////////////////////////////////////////-->

</body>
<style>
    span{
        /*margin-top: -40px;*/
        /*margin-left: 20%;*/
        font-size: 60px;
    }

</style>
</html>
