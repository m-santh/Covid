﻿using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.SceneManagement;

public class launchApp : MonoBehaviour
{
    public string scene;

    public void launchMainScene()
    {
        SceneManager.LoadScene(scene);
    }
}
