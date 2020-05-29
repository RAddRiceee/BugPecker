<?php
/*
Credits: Bit Repository
URL: http://www.bitrepository.com/
*/

include dirname(dirname(__FILE__)).'/config.php';

error_reporting (E_ALL ^ E_NOTICE);

$post = (!empty($_POST)) ? true : false;

if($post)
{
include 'functions.php';

$email = trim($_POST['e-mail']);

$error = '';

// Check email

if(!$email)
{
$error .= 'Please enter an e-mail address.';
}

if($email && !ValidateEmail($email))
{
$error .= 'Please enter a valid e-mail address.';
}

// Check message (length)


if(!$error)
{
ini_set("sendmail_from", WEBMASTER_EMAIL); // for windows server

// The e-mail message being sent to the admin - you can change the content of the message below.
$mail = mail(WEBMASTER_EMAIL, "Newsletter Signup Request for Booom! Template", "Sweet, you can add " . $email . " to the newsletter mailing list!",
     "From: <".$email.">\r\n"
    ."Reply-To: ".$email."\r\n"
    ."X-Mailer: PHP/" . phpversion());


if($mail)
{
echo 'OK';
}

}
else
{
echo '<div class="alert alert-error">'.$error.'</div>';
}

}
?>